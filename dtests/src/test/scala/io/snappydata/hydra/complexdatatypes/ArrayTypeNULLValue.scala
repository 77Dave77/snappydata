/*
 * Copyright (c) 2017-2019 TIBCO Software Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */
package io.snappydata.hydra.complexdatatypes

import java.io.{File, FileOutputStream, PrintWriter}

import com.typesafe.config.Config
import org.apache.spark.sql._

class ArrayTypeNULLValue extends SnappySQLJob {
  override def isValidJob(sc: SnappySession, config: Config): SnappyJobValidation = SnappyJobValid()

  override def runSnappyJob(snappySession: SnappySession, jobConfig: Config): Any = {
    // scalastyle:off println
    println("ArraysTypeNULLValue Job started...")
    val snc : SnappyContext = snappySession.sqlContext
    //  def getCurrentDirectory = new java.io.File(".").getCanonicalPath()
    val outputFile = "ValidateArrayTypeNULLValue" + "_"  +
      System.currentTimeMillis() + jobConfig.getString("logFileName")
    val pw : PrintWriter = new PrintWriter(new FileOutputStream(new File(outputFile), false))

    /**
      *  Test : NULL value in Complex Type column.
      */
    snc.sql("create schema st")
    snc.sql("create table if not exists st.Student" +
      "(rollno int,name String, adminDate Array<Date>) using column")
    snc.sql("insert into st.Student select 1, 'ABC', null")
    snc.sql("insert into st.Student select 2,'XYZ',Array('2020-01-21')")
    val resultDF1 = snc.sql("select * from st.Student")
    val resultSet1 = resultDF1.collectAsList()
    pw.println("ResultSet1 : ")
    pw.println(resultSet1)
    snc.sql("drop table st.Student")
    // Place the Array Type column in between some data types.
    snc.sql("create table if not exists st.Student" +
      "(rollno int,adminDate Array<Date>,time TimeStamp, class int) using column")
    snc.sql("insert into st.Student select 1,Array('2020-01-21'), current_timestamp(),5")
    snc.sql("insert into st.Student select 1,null,null,6")
    val resultDF2 = snc.sql("select * from st.Student")
    val resultSet2 = resultDF2.collectAsList()
    pw.println("ResultSet2 : ")
    pw.println(resultSet2)
    snc.sql("drop table st.Student")
    snc.sql("drop schema st")
    pw.println("Inserting NULL value in ArrayType column  check OK")
    pw.flush()
    pw.close()
  }
}