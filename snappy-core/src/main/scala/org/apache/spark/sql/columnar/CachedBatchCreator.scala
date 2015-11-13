package org.apache.spark.sql.columnar

import java.sql.ResultSet
import java.util.UUID

import scala.collection.mutable.ArrayBuffer

import org.apache.spark.sql.catalyst.{CatalystTypeConverters, InternalRow}
import org.apache.spark.sql.catalyst.expressions.{Attribute, SpecificMutableRow}
import org.apache.spark.sql.collection.UUIDRegionKey
import org.apache.spark.sql.store.ExternalStore
import org.apache.spark.sql.types._
import org.apache.spark.sql.catalyst.util.DateTimeUtils
import org.apache.spark.unsafe.types.UTF8String

/**
 * Created by skumar on 5/11/15.
 */
class CachedBatchCreator(
    val tableName: String,
    val schema: StructType,
    val externalStore: ExternalStore) {

  abstract class JDBCConversion

  case object BooleanConversion extends JDBCConversion

  case object DateConversion extends JDBCConversion

  case class DecimalConversion(precision: Int, scale: Int) extends JDBCConversion

  case object DoubleConversion extends JDBCConversion

  case object FloatConversion extends JDBCConversion

  case object IntegerConversion extends JDBCConversion

  case object LongConversion extends JDBCConversion

  case object BinaryLongConversion extends JDBCConversion

  case object StringConversion extends JDBCConversion

  case object TimestampConversion extends JDBCConversion

  case object BinaryConversion extends JDBCConversion

  /**
   * Maps a StructType to a type tag list.
   */
  def getConversions(schema: StructType): Array[JDBCConversion] = {
    schema.fields.map(sf => sf.dataType match {
      case BooleanType => BooleanConversion
      case DateType => DateConversion
      case DecimalType.Fixed(p, s) => DecimalConversion(p, s)
      case DoubleType => DoubleConversion
      case FloatType => FloatConversion
      case IntegerType => IntegerConversion
      case LongType =>
        if (sf.metadata.contains("binarylong")) BinaryLongConversion else LongConversion
      case StringType => StringConversion
      case TimestampType => TimestampConversion
      case BinaryType => BinaryConversion
      case _ => throw new IllegalArgumentException(s"Unsupported field $sf")
    }).toArray
  }

  def createInternalRow(rs: ResultSet): InternalRow = {
    val conversions = getConversions(schema)
    val mutableRow = new SpecificMutableRow(schema.fields.map(x => x.dataType))

    var i = 0
    while (i < conversions.length) {
      val pos = i + 1
      conversions(i) match {
        case BooleanConversion => mutableRow.setBoolean(i, rs.getBoolean(pos))
        case DateConversion =>
          // DateTimeUtils.fromJavaDate does not handle null value, so we need to check it.
          val dateVal = rs.getDate(pos)
          if (dateVal != null) {
            mutableRow.setInt(i, DateTimeUtils.fromJavaDate(dateVal))
          } else {
            mutableRow.update(i, null)
          }
        // When connecting with Oracle DB through JDBC, the precision and scale of BigDecimal
        // object returned by ResultSet.getBigDecimal is not correctly matched to the table
        // schema reported by ResultSetMetaData.getPrecision and ResultSetMetaData.getScale.
        // If inserting values like 19999 into a column with NUMBER(12, 2) type, you get through
        // a BigDecimal object with scale as 0. But the dataframe schema has correct type as
        // DecimalType(12, 2). Thus, after saving the dataframe into parquet file and then
        // retrieve it, you will get wrong result 199.99.
        // So it is needed to set precision and scale for Decimal based on JDBC metadata.
        case DecimalConversion(p, s) =>
          val decimalVal = rs.getBigDecimal(pos)
          if (decimalVal == null) {
            mutableRow.update(i, null)
          } else {
            mutableRow.update(i, Decimal(decimalVal, p, s))
          }
        case DoubleConversion => mutableRow.setDouble(i, rs.getDouble(pos))
        case FloatConversion => mutableRow.setFloat(i, rs.getFloat(pos))
        case IntegerConversion => mutableRow.setInt(i, rs.getInt(pos))
        case LongConversion => mutableRow.setLong(i, rs.getLong(pos))
        // TODO(davies): use getBytes for better performance, if the encoding is UTF-8
        case StringConversion => mutableRow.update(i, UTF8String.fromString(rs.getString(pos)))
        case TimestampConversion =>
          val t = rs.getTimestamp(pos)
          if (t != null) {
            mutableRow.setLong(i, DateTimeUtils.fromJavaTimestamp(t))
          } else {
            mutableRow.update(i, null)
          }
        case BinaryConversion => mutableRow.update(i, rs.getBytes(pos))
        case BinaryLongConversion => {
          val bytes = rs.getBytes(pos)
          var ans = 0L
          var j = 0
          while (j < bytes.size) {
            ans = 256 * ans + (255 & bytes(j))
            j = j + 1;
          }
          mutableRow.setLong(i, ans)
        }
      }
      if (rs.wasNull) mutableRow.setNullAt(i)
      i = i + 1
    }
    mutableRow
  }

  def createCachedBatch(rowIterator: Iterator[InternalRow], batchID :UUID, bucketID : Int): Unit = {
    val useCompression = true
    val columnBatchSize = 10000 //TODO: Suranjan Get from somewhere

    def uuidBatchAggregate (accumulated: ArrayBuffer[UUIDRegionKey],
        batch: CachedBatch): ArrayBuffer[UUIDRegionKey] = {
      val uuid = externalStore.storeCachedBatch (batch, batchID, bucketID, tableName)
      accumulated += uuid
    }

    def columnBuilders = schema.map {
      attribute =>
        val columnType = ColumnType (attribute.dataType)
        val initialBufferSize = columnType.defaultSize * columnBatchSize
        ColumnBuilder (attribute.dataType, initialBufferSize,
          attribute.name, useCompression)
    }.toArray

    val holder = new CachedBatchHolder (columnBuilders, 0, columnBatchSize, schema,
      new ArrayBuffer[UUIDRegionKey] (1), uuidBatchAggregate)

    val batches = holder.asInstanceOf[CachedBatchHolder[ArrayBuffer[Serializable]]]
    rowIterator.foreach (batches.appendRow ((), _) )

    batches.forceEndOfBatch
  }
}
