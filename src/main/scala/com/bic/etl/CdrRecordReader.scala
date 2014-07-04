package com.bic.etl

import org.apache.hadoop.mapreduce.{TaskAttemptContext, InputSplit}
import org.apache.hadoop.mapreduce.lib.input.{FileSplit, LineRecordReader}
import org.apache.hadoop.io._
import org.apache.spark.Logging

/**
 * Created by patrick.jiang on 6/12/14.
 *
 */
class CdrRecordReader(recordDelimiter: Array[Byte]) extends LineRecordReader(recordDelimiter) with Logging {
  //  private var unixmtime: Long = 0L
  private var ndts: String = ""

  override def initialize(genericSplit: InputSplit, context: TaskAttemptContext): Unit = {
    super.initialize(genericSplit, context)
    val split: FileSplit = genericSplit.asInstanceOf[FileSplit]
    val m = """(\d{2,4}-\d{2}-\d{2})""".r.findAllIn(split.getPath.getName).toList
    //    val operatorInfo = m.toList.tail.foldRight("")((a, b) => a + " " + b)
    //    val operatorInfo =
    //      ((m.tail foldLeft (new StringBuilder(m.head))) { (acc, e) => acc.append(",").append(e)}).toString
    val operatorInfo = m.mkString(" ")
    val platformInfo = m.mkString(" ")
    //    val odts = operatorInfo.trim
    logInfo(operatorInfo)
    ndts = operatorInfo
    //    val ofmt = DateTimeFormat.forPattern("yyyy-MM-dd HH-mm-ss")
    //    val odt = ofmt.parseDateTime(odts)
    //    val nfmt = DateTimeFormat.forPattern("yyyy-MM-dd")
    //    ndts = odt.toString(nfmt)
    //    unixmtime = fmt.parseDateTime(operatorInfo).getMillis
  }

  override def getCurrentValue: Text = {
    var value = super.getCurrentValue
    //    logInfo("o="+value.toString)
    value.set(ndts + ";" + value.toString)
    //    logInfo("n=" + value.toString)
    value
  }

  override def getCurrentKey: LongWritable = {
    super.getCurrentKey
  }

  override def nextKeyValue(): Boolean = {
    super.nextKeyValue()
  }
}

