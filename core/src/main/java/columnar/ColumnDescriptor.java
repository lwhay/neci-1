/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package columnar;

import java.io.IOException;
import java.util.Arrays;

import org.apache.trevni.Input;

import codec.Checksum;
import io.InputBuffer;
import metadata.FileColumnMetaData;

public class ColumnDescriptor<T extends Comparable> {
    final BlockManager bm;
    final Input dataFile;
    public final FileColumnMetaData metaData;

    long start;

    public CompressedBlockDescriptor[] blocks;

    public long[] blockStarts; // for random access
    public int[] firstRows; // for binary searches
    public T[] firstValues; // for binary searches

    public ColumnDescriptor(Input dataFile, FileColumnMetaData metaData, BlockManager bm) {
        this.dataFile = dataFile;
        this.metaData = metaData;
        this.bm = bm;
    }

    public Input getDataFile() {
        return dataFile;
    }

    public BlockManager getBlockManager() {
        return bm;
    }

    public String getCodecName() {
        return metaData.getCodec();
    }

    public void setBlockDescriptor(CompressedBlockDescriptor[] blocks) {
        this.blocks = blocks;
    }

    public int findBlock(int row) {
        int block = Arrays.binarySearch(firstRows, row);
        if (block < 0)
            block = -block - 2;
        return block;
    }

    public int findBlock(T value) {
        int block = Arrays.binarySearch(firstValues, value);
        if (block < 0)
            block = -block - 2;
        return block;
    }

    public int blockCount() {
        return blocks.length;
    }

    public int lastRow() {
        int len = blocks.length;
        if (len == 0)
            return 0;
        return firstRows[len - 1] + blocks[len - 1].rowCount;
    }

    public int lastRow(int block) {
        if (blocks.length == 0 || block < 0)
            return 0;
        return firstRows[block] + blocks[block].rowCount;
    }

    public void ensureBlocksRead() throws IOException {
        //    if (blocks != null) return;

        // read block descriptors
        InputBuffer in = new InputBuffer(bm, dataFile, start);
        int blockCount = blocks.length;
        //    int blockCount = in.readFixed32();
        //    BlockDescriptor[] blocks = new BlockDescriptor[blockCount];
        if (metaData.hasIndexValues())
            firstValues = (T[]) new Comparable[blockCount];
        //    for (int i = 0; i < blockCount; i++) {
        //      blocks[i] = BlockDescriptor.read(in);
        //      if (metaData.hasIndexValues())
        //        firstValues[i] = in.<T>readValue(metaData.getType());
        //    }
        //    dataStart = in.tell();

        // compute blockStarts and firstRows

        Checksum checksum = Checksum.get(metaData);
        blockStarts = new long[blocks.length];
        firstRows = new int[blocks.length];
        long startPosition = start;
        int row = 0;
        for (int i = 0; i < blockCount; i++) {
            CompressedBlockDescriptor b = blocks[i];
            blockStarts[i] = startPosition;
            firstRows[i] = row;
            startPosition += b.getCompressedSize() + checksum.size();
            row += b.rowCount;
        }
    }

}
