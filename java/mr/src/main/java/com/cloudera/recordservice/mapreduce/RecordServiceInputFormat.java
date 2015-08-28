// Confidential Cloudera Information: Covered by NDA.
// Copyright 2012 Cloudera Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.cloudera.recordservice.mapreduce;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.security.Credentials;

import com.cloudera.recordservice.core.RecordServiceException;
import com.cloudera.recordservice.core.Records;
import com.cloudera.recordservice.core.Records.Record;
import com.cloudera.recordservice.mr.RecordReaderCore;
import com.cloudera.recordservice.mr.RecordServiceRecord;
import com.cloudera.recordservice.mr.Schema;
import com.google.common.annotations.VisibleForTesting;

/**
 * Input format which returns <NULL, RecordServiceReceord>
 * TODO: is this useful? This introduces the RecordServiceRecord "object"
 * model.
 */
public class RecordServiceInputFormat extends
    RecordServiceInputFormatBase<NullWritable, RecordServiceRecord> {

  @Override
  public RecordReader<NullWritable, RecordServiceRecord>
      createRecordReader(InputSplit split, TaskAttemptContext context)
          throws IOException, InterruptedException {
    RecordServiceRecordReader rReader = new RecordServiceRecordReader();
    rReader.initialize(split, context);
    return rReader;
  }

  /**
   * RecordReader implementation that uses the RecordService for data access. Values
   * are returned as RecordServiceRecords, which contain schema and data for a single
   * record.
   * To reduce the creation of new objects, existing storage is reused for both
   * keys and values (objects are updated in-place).
   */
  public static class RecordServiceRecordReader extends
      RecordReaderBase<NullWritable, RecordServiceRecord> {
    // Current record being processed
    private Records.Record currentRSRecord_;

    // The record that is returned. Updated in-place when nextKeyValue() is called.
    private RecordServiceRecord record_;

    // True after initialize() has fully completed.
    private volatile boolean isInitialized_ = false;

    /**
     * The general contract of the RecordReader is that the client (Mapper) calls
     * this method to load the next Key and Value.. before calling getCurrentKey()
     * and getCurrentValue().
     *
     * Returns true if there are more values to retrieve, false otherwise.
     */
    @Override
    public boolean nextKeyValue() throws IOException, InterruptedException {
      if (!isInitialized_) {
        throw new RuntimeException("Record Reader not initialized !!");
      }

      if (!nextRecord()) return false;
      record_.reset(currentRSRecord_);
      return true;
    }

    @Override
    public NullWritable getCurrentKey() throws IOException, InterruptedException {
      return NullWritable.get();
    }

    @Override
    public RecordServiceRecord getCurrentValue() throws IOException,
        InterruptedException {
      return record_;
    }

    /**
     * Advances to the next record. Return false if there are no more records.
     */
    public boolean nextRecord() throws IOException {
      if (!isInitialized_) {
        throw new RuntimeException("Record Reader not initialized !!");
      }
      try {
        if (!reader_.records().hasNext()) return false;
      } catch (RecordServiceException e) {
        // TODO: is this the most proper way to deal with this in MR?
        throw new IOException("Could not fetch record.", e);
      }
      currentRSRecord_ = reader_.records().next();
      return true;
    }

    public Schema schema() { return reader_.schema(); }
    public Record currentRecord() { return currentRSRecord_; }
    public boolean isInitialized() { return isInitialized_; }

    @VisibleForTesting
    void initialize(RecordServiceInputSplit split, Configuration config)
        throws IOException {
      try {
        reader_ = new RecordReaderCore(config, new Credentials(), split.getTaskInfo());
      } catch (Exception e) {
        throw new IOException("Failed to execute task.", e);
      }
      record_ = new RecordServiceRecord(schema());
      isInitialized_ = true;
    }
  }
}
