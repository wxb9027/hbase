/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.util.compaction;

import java.io.IOException;
import java.util.Random;
import com.google.common.collect.Sets;
import org.apache.hadoop.hbase.HBaseTestingUtility;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.testclassification.MediumTests;
import org.apache.hadoop.hbase.testclassification.MiscTests;
import org.apache.hadoop.hbase.util.Bytes;
import org.junit.After;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;


@Category({ MiscTests.class, MediumTests.class })
public class MajorCompactorTest {

  public static final byte[] FAMILY = Bytes.toBytes("a");
  protected HBaseTestingUtility utility;
  protected HBaseAdmin admin;

  @Before public void setUp() throws Exception {
    utility = new HBaseTestingUtility();
    utility.getConfiguration().setInt("hbase.hfile.compaction.discharger.interval", 10);
    utility.startMiniCluster();
  }

  @After public void tearDown() throws Exception {
    utility.shutdownMiniCluster();
  }

  @Test public void testCompactingATable() throws Exception {
    TableName tableName = TableName.valueOf("MajorCompactorTest");
    utility.createMultiRegionTable(tableName, FAMILY, 5);
    utility.waitTableAvailable(tableName);
    Connection connection = utility.getConnection();
    Table table = connection.getTable(tableName);
    // write data and flush multiple store files:
    for (int i = 0; i < 5; i++) {
      loadRandomRows(table, FAMILY, 50, 100);
      utility.flush(tableName);
    }
    table.close();
    int numberOfRegions = utility.getHBaseAdmin().getTableRegions(tableName).size();
    int numHFiles = utility.getNumHFiles(tableName, FAMILY);
    // we should have a table with more store files than we would before we major compacted.
    assertTrue(numberOfRegions < numHFiles);

    MajorCompactor compactor =
        new MajorCompactor(utility.getConfiguration(), tableName,
            Sets.newHashSet(Bytes.toString(FAMILY)), 1, System.currentTimeMillis(), 200);
    compactor.initializeWorkQueues();
    compactor.compactAllRegions();
    compactor.shutdown();

    // verify that the store has been completely major compacted.
    numberOfRegions = utility.getHBaseAdmin().getTableRegions(tableName).size();
    numHFiles = utility.getNumHFiles(tableName, FAMILY);
    assertEquals(numHFiles, numberOfRegions);
  }

  protected void loadRandomRows(final Table t, final byte[] f, int rowSize, int totalRows)
      throws IOException {
    Random r = new Random();
    byte[] row = new byte[rowSize];
    for (int i = 0; i < totalRows; i++) {
      r.nextBytes(row);
      Put put = new Put(row);
      put.addColumn(f, new byte[]{0}, new byte[]{0});
      t.put(put);
    }
  }
}