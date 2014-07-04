package com.bic.etl

import com.google.common.base.Charsets
import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat
import org.apache.hadoop.mapreduce.{InputSplit, JobContext, RecordReader, TaskAttemptContext}

/**
 * Created by patrick.jiang on 6/12/14.
 *
 */
class CdrInputFormat extends TextInputFormat {
  override def createRecordReader(split: InputSplit, context: TaskAttemptContext): RecordReader[LongWritable, Text] = {
    //    super.createRecordReader(split, context)
    val delimiter: String = context.getConfiguration.get("textinputformat.record.delimiter", "\n")
    var recordDelimiterBytes: Array[Byte] = null
    if (null != delimiter) recordDelimiterBytes = delimiter.getBytes(Charsets.UTF_8)
    new CdrRecordReader(recordDelimiterBytes)
  }

  override def isSplitable(context: JobContext, file: Path): Boolean = false
}
