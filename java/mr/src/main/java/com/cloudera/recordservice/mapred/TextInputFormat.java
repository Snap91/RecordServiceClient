// Confidential Cloudera Information: Covered by NDA.
package com.cloudera.recordservice.mapred;

import java.io.IOException;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.InputSplit;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.RecordReader;
import org.apache.hadoop.mapred.Reporter;

import com.cloudera.recordservice.core.ByteArray;
import com.cloudera.recordservice.mr.Schema.ColumnType;
import com.cloudera.recordservice.thrift.TRecordServiceException;

/**
 * Input format that implements the mr TextInputFormat.
 * This only works if the schema of the data is 'STRING'.
 */
public class TextInputFormat extends RecordServiceInputFormatBase<LongWritable, Text> {
  @Override
  public InputSplit[] getSplits(JobConf job, int numSplits) throws IOException {
    com.cloudera.recordservice.mapreduce.RecordServiceInputFormatBase.SplitsInfo splits =
        com.cloudera.recordservice.mapreduce.RecordServiceInputFormatBase.getSplits(job);
    com.cloudera.recordservice.mapreduce.TextInputFormat.verifyTextSchema(splits.schema);
    return convertSplits(splits.splits);
  }

  @Override
  public RecordReader<LongWritable, Text> getRecordReader(InputSplit split,
      JobConf job, Reporter reporter) throws IOException {
    return new TextRecordReader((RecordServiceInputSplit)split, job, reporter);
  }

  private static final class TextRecordReader
      extends RecordReaderBase<LongWritable, Text> {
    private long recordNum_ = 0;

    public TextRecordReader(RecordServiceInputSplit split, JobConf config,
        Reporter reporter) throws IOException {
      super(split, config, reporter);
      // TODO: reimplement guava's Preconditions instead of assert.
      assert(reader_.schema().getNumColumns() == 1);
      assert(reader_.schema().getColumnInfo(0).getType() == ColumnType.STRING);
    }

    @Override
    public boolean next(LongWritable key, Text value) throws IOException {
      try {
        if (!reader_.records().hasNext()) return false;
        ByteArray data = reader_.records().next().getByteArray(0);
        value.set(data.byteBuffer().array(), data.offset(), data.len());
        key.set(recordNum_++);
        return true;
      } catch (TRecordServiceException e) {
        throw new IOException("Could not get next record.", e);
      }
    }

    @Override
    public LongWritable createKey() {
      return new LongWritable();
    }

    @Override
    public Text createValue() {
      return new Text();
    }
  }
}