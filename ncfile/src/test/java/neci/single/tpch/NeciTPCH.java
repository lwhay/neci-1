/**
 * 
 */
package neci.single.tpch;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import neci.ncfile.FilterBatchColumnReader;
import neci.ncfile.FilterOperator;
import neci.ncfile.base.Schema;
import neci.ncfile.generic.GenericData.Record;
import neci.parallel.tpch.filter.CnationkeyContainedbyFilter;
import neci.parallel.tpch.filter.DiscountBetweenFilter;
import neci.parallel.tpch.filter.LsuppkeyContainedbyFilter;
import neci.parallel.tpch.filter.MktsegmentEqualFilter;
import neci.parallel.tpch.filter.OrderdateLeftBetweenFilter;
import neci.parallel.tpch.filter.OrderdateSmallerFilter;
import neci.parallel.tpch.filter.PSsuppkeyContainedbyFilter;
import neci.parallel.tpch.filter.PsizeEqualFilter;
import neci.parallel.tpch.filter.PtypeLikeFilter;
import neci.parallel.tpch.filter.QuantitySmallerFilter;
import neci.parallel.tpch.filter.ReturnflagEqualFilter;
import neci.parallel.tpch.filter.ShipdateBetweenFilter;
import neci.parallel.tpch.filter.ShipdateLargerFilter;
import neci.parallel.tpch.filter.ShipdateLeftBetweenFilter;
import neci.parallel.tpch.filter.ShipdateSmallerEqualFilter;
import neci.single.ScanCompare;
import utils.tpch.SupplierHelper;

/**
 * @author lwh
 *
 */
public class NeciTPCH extends ScanCompare {
    private static final boolean comp = true;

    public static void Q01_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[1];
        filters[0] = new ShipdateSmallerEqualFilter("1998-09-01");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int count = 0;
        reader.filter();
        reader.createFilterRead(max);
        /*@SuppressWarnings("unused")
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        @SuppressWarnings("unused")
        int offsetLines = orderSchema.getFields().size() - 1;*/
        Map<ByteBuffer, Map<ByteBuffer, float[]>> values = new HashMap<>();
        int valuecount = 0;
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }

            ByteBuffer rf = (ByteBuffer) r.get("l_returnflag");
            ByteBuffer ls = (ByteBuffer) r.get("l_linestatus");
            if (!values.containsKey(rf)) {
                values.put(rf, new HashMap<>());
            }
            if (!values.get(rf).containsKey(ls)) {
                values.get(rf).put(ls, new float[6]);
                valuecount++;
            }
            float[] aggs = values.get(rf).get(ls);
            aggs[0] += (float) r.get("l_quantity");
            float ep = (float) r.get("l_extendedprice");
            float dc = (float) r.get("l_discount");
            float tx = (float) r.get("l_tax");
            aggs[1] += ep;
            aggs[2] += dc;
            aggs[3] += ep * (1 - dc);
            aggs[4] += ep * (1 - dc) * (1 + tx);
            aggs[5]++;
            values.get(rf).put(ls, aggs);
            count++;
        }
        long end = System.currentTimeMillis();
        System.out.println(valuecount);
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + values.size() + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q02_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        BitSet indicators = new BitSet();
        indicators.set(1, 7);
        SupplierHelper minSuppcost = new SupplierHelper("EUROPE", indicators);
        minSuppcost.init();
        Map<Long, String[]> minSuppcosts = minSuppcost.getValidSuppCosts();
        Map<Integer, String> nationNames = minSuppcost.getNationNames();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[3];
        filters[0] = new PsizeEqualFilter(15);
        filters[1] = new PtypeLikeFilter("BRASS$");
        filters[2] = new PSsuppkeyContainedbyFilter(minSuppcosts.keySet());
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int offsetPS = readSchema.getFields().size() - 1;
        Map<Float, List<String[]>> values = new HashMap<>();
        int count = 0;
        reader.filter();
        reader.createFilterRead(max);
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            String partkey = r.get("p_partkey").toString();
            String mfgr = r.get("p_mfgr").toString();
            @SuppressWarnings("unchecked")
            List<Record> psl = (List<Record>) r.get(offsetPS);
            float minCost = Float.MAX_VALUE;
            int minIdx = -1;
            int cursor = 0;
            for (Record psr : psl) {
                float suppcost = (float) psr.get("ps_supplycost");
                if (suppcost < minCost) {
                    minIdx = cursor;
                }
                cursor++;
            }
            String[] sloads = minSuppcosts.get((long) psl.get(minIdx).get("ps_suppkey"));
            if (values.containsKey(Float.parseFloat(sloads[4]))) {
                values.put(Float.parseFloat(sloads[4]), new ArrayList<>());
            }
            String[] loads = new String[7];
            loads[0] = sloads[0];
            loads[1] = nationNames.get(Integer.parseInt(sloads[2]));
            loads[2] = partkey;
            loads[3] = mfgr;
            loads[4] = sloads[1];
            loads[5] = sloads[3];
            loads[6] = sloads[5];
        }
        long end = System.currentTimeMillis();
        System.out.println(values.size());
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + values.size() + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q03_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[3];
        filters[0] = new MktsegmentEqualFilter("BUILDING");
        filters[1] = new OrderdateSmallerFilter("1995-03-15");
        filters[2] = new ShipdateLargerFilter("1995-03-15");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int count = 0;
        try {
            reader.filter();
        } catch (Exception e) {
            System.out.println("to be handled 1");
            reader.close();
            System.exit(-1);
        }
        double result = 0.00;
        double sum = 0.00;
        reader.createFilterRead(max);
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Map<Long, Float>> values = new HashMap<>();
        while (reader.hasNext()) {
            Record r = null;
            try {
                r = reader.next();
            } catch (Exception e) {
                System.out.println("to be handled 2");
                reader.close();
                System.exit(-1);
            }
            if (!comp || r == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            for (Record order : orders) {
                Long orderkey = (long) order.get("o_orderkey");
                String dateship =
                        order.get("o_orderkey").toString().trim() + "|" + order.get("o_shippriority").toString().trim();
                if (!values.containsKey(dateship)) {
                    values.put(dateship, new HashMap<Long, Float>());
                }
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                float value = .0f;
                for (Record line : lines) {
                    value += (float) line.get("l_extendedprice") * (1 - (float) line.get("l_discount"));
                    count++;
                }
                if (!values.get(dateship).containsKey(orderkey)) {
                    values.get(dateship).put(orderkey, .0f);
                }
                values.get(dateship).put(orderkey, value);
            }
            //result += (float) r.get(1) * (float) r.get(2);
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        System.out.println(values.size());
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        System.out.println("revenue: " + nf.format(result));
    }

    public static void Q04_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[1];
        filters[0] = new OrderdateLeftBetweenFilter("1993-07-01", "1993-10-01");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Integer> values = new HashMap<>();
        int count = 0;
        reader.filter();
        reader.createFilterRead(max);
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            for (Record order : orders) {
                String priority = order.get("o_priority").toString();
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                boolean contain = false;
                for (Record line : lines) {
                    String commitdate = line.get("l_commitdate").toString();
                    String receiptdate = line.get("l_receiptdate").toString();
                    if (commitdate.compareTo(receiptdate) < 0) {
                        contain = true;
                        count++;
                    }
                }
                if (contain) {
                    if (!values.containsKey(priority)) {
                        values.put(priority, 0);
                    }
                    values.put(priority, values.get(priority) + 1);
                }
            }
        }
        long end = System.currentTimeMillis();
        System.out.println(values.size());
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + values.size() + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q05_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        BitSet indicators = new BitSet();
        indicators.set(3);
        SupplierHelper minSuppcost = new SupplierHelper("ASIA", indicators);
        minSuppcost.init();
        Map<Long, String[]> supplyNations = minSuppcost.getValidSuppCosts();
        Map<Integer, String> nationNames = minSuppcost.getNationNames();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[3];
        // We have applied innermost verification for the equality of equal-region supplier and customer.
        filters[0] = new CnationkeyContainedbyFilter(nationNames.keySet());
        filters[1] = new OrderdateLeftBetweenFilter("1994-01-01", "1995-01-01");
        filters[2] = new LsuppkeyContainedbyFilter(supplyNations.keySet());
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int count = 0;
        reader.filter();
        double result = 0.00;
        double sum = 0.00;
        reader.createFilterRead(max);
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Float> values = new HashMap<>();
        int redundant = 0;
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            int c_nationkey = (int) r.get("c_nationkey");
            /*System.out.println(nationNames.get(c_nationkey));*/
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            float value = .0f;
            for (Record order : orders) {
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                for (Record line : lines) {
                    long l_suppkey = (long) line.get("l_suppkey");
                    if (Integer.parseInt(supplyNations.get(l_suppkey)[0]) == c_nationkey) {
                        value += (float) line.get("l_extendedprice") * (1 - (float) line.get("l_discount"));
                    } else {
                        /*System.out.println("l-s-nk: " + Integer.parseInt(supplyNations.get(l_suppkey)[0]) + " c_nk: "
                                + c_nationkey + " <-> " + nationNames.get(c_nationkey));*/
                        redundant++;
                    }
                    count++;
                }
            }
            String nationName = nationNames.get(c_nationkey);
            if (!values.containsKey(nationName)) {
                values.put(nationName, .0f);
            }
            values.put(nationName, values.get(nationName) + value);
            //result += (float) r.get(1) * (float) r.get(2);
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        System.out.println(values.size() + " fine-grained: " + count + " redundant: " + redundant);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q06_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[3];
        filters[0] = new QuantitySmallerFilter(24);
        filters[1] = new DiscountBetweenFilter(0.05f, 0.07f);
        filters[2] = new ShipdateLeftBetweenFilter("1994-01-01", "1995-01-01");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int count = 0;
        reader.filter();
        reader.createFilterRead(max);
        int valuecount = reader.getRowCount(reader.getValidColumnNO("l_discount"));
        float result = 0;
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }

            float ep = (float) r.get("l_extendedprice");
            float dc = (float) r.get("l_discount");
            result += ep * dc;
            count++;
        }
        long end = System.currentTimeMillis();
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count + " out of " + valuecount);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q07_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        Set<String> nations = new HashSet<>();
        nations.add("GERMANY");
        nations.add("FRANCE");
        BitSet indicators = new BitSet();
        indicators.set(3);
        SupplierHelper minSuppcost = new SupplierHelper("ANY", nations, indicators);
        minSuppcost.init();
        Map<Long, String[]> supplyNations = minSuppcost.getValidSuppCosts();
        Map<Long, Integer> supplyNationKeys = new HashMap<>();
        for (Entry<Long, String[]> entry : supplyNations.entrySet()) {
            supplyNationKeys.put(entry.getKey(), Integer.parseInt(entry.getValue()[0]));
        }
        Map<Integer, String> nationNames = minSuppcost.getNationNames();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[3];
        // We have applied innermost verification for the equality of equal-region supplier and customer.
        filters[0] = new CnationkeyContainedbyFilter(nationNames.keySet());
        filters[1] = new LsuppkeyContainedbyFilter(supplyNations.keySet());
        filters[2] = new ShipdateBetweenFilter("1995-01-01", "1996-12-31");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        int count = 0;
        reader.filter();
        double result = 0.00;
        double sum = 0.00;
        reader.createFilterRead(max);
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Float> values = new HashMap<>();
        int redundant = 0;
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            int c_nationkey = (int) r.get("c_nationkey");
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            for (Record order : orders) {
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                for (Record line : lines) {
                    long l_suppkey = (long) line.get("l_suppkey");
                    int suppNationKey = supplyNationKeys.get(l_suppkey);
                    if (suppNationKey != c_nationkey) {
                        String key = "";
                        key += nationNames.get(c_nationkey);
                        key += "|";
                        key += nationNames.get(suppNationKey);
                        key += "|";
                        key += line.get("l_shipdate").toString().substring(0, 4);
                        float value = .0f;
                        if (values.containsKey(key)) {
                            value = values.get(key);
                        }
                        value += (float) line.get("l_extendedprice") * (1 - (float) line.get("l_discount"));
                        values.put(key, value);
                        count++;
                    } else {
                        redundant++;
                    }
                }
            }
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        System.out.println(values.size() + " fine-grained: " + count + " redundant: " + redundant);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
    }

    public static void Q10_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[2];
        filters[0] = new OrderdateLeftBetweenFilter("1993-10-01", "1994-01-01");
        filters[1] = new ReturnflagEqualFilter(ByteBuffer.wrap("R".getBytes()));
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        //reader.createRead(max);
        int count = 0;
        reader.filter();
        double result = 0.00;
        double sum = 0.00;
        reader.createFilterRead(max);
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Map<Long, Float>> values = new HashMap<>();
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            long ck = (long) r.get("c_custkey");
            String ckey = r.get("c_acctbal").toString().trim() + "|" + r.get("c_address").toString().trim() + "|"
                    + (int) r.get("c_nationkey") + "|" + r.get("c_comment").toString().trim();
            if (!values.containsKey(ckey)) {
                values.put(ckey, new HashMap<Long, Float>());
            }
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            float value = .0f;
            for (Record order : orders) {
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                for (Record line : lines) {
                    value += (float) line.get("l_extendedprice") * (1 - (float) line.get("l_discount"));
                    count++;
                }
            }
            if (!values.get(ckey).containsKey(ck)) {
                values.get(ckey).put(ck, value);
            } else {
                values.get(ckey).put(ck, values.get(ckey).get(ck) + value);
            }
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        System.out.println(values.size());
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        System.out.println("revenue: " + nf.format(result));
    }

    public static void Q15_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        @SuppressWarnings("rawtypes")
        FilterOperator[] filters = new FilterOperator[1];
        filters[0] = new ShipdateLeftBetweenFilter("1994-03-02", "1994-06-02");
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, filters, blockSize);
        reader.createSchema(readSchema);
        //reader.createRead(max);
        int count = 0;
        reader.filter();
        double result = 0.00;
        double sum = 0.00;
        reader.createFilterRead(max);
        //Set<String> date = new HashSet<String>();
        //Set<Byte> flag = new HashSet<>();
        String name = readSchema.getFields().get(0).name();
        int columnNo = reader.getValidColumnNO(name);
        System.out.println("line: " + reader.getRowCount(columnNo));
        while (reader.hasNext()) {
            Record r = reader.next();
            //System.out.println(r);
            if (!comp) {
                continue;
            }
            result += (float) r.get("l_extendedprice") * (float) r.get("l_discount");
            count++;
            //System.out.println(result);
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        System.out.println("revenue: " + nf.format(result));
    }

    public static void Q81_FilteringScan(String[] args) throws IOException {
        File file = new File(args[0]);
        Schema readSchema = new Schema.Parser().parse(new File(args[1]));
        int max = Integer.parseInt(args[2]);
        int blockSize = Integer.parseInt(args[4]);
        long start = System.currentTimeMillis();
        FilterBatchColumnReader<Record> reader = new FilterBatchColumnReader<Record>(file, blockSize);
        reader.createSchema(readSchema);
        //reader.createRead(max);
        int count = 0;
        double result = 0.00;
        double sum = 0.00;
        reader.createRead(max);
        //Set<String> date = new HashSet<String>();
        //Set<Byte> flag = new HashSet<>();
        String name = readSchema.getFields().get(0).name();
        int columnNo = reader.getValidColumnNO(name);
        System.out.println("line: " + reader.getRowCount(columnNo));
        int offsetOrders = readSchema.getFields().size() - 1;
        Schema orderSchema = readSchema.getFields().get(readSchema.getFields().size() - 1).schema().getElementType();
        int offsetLines = orderSchema.getFields().size() - 1;
        Map<String, Float> results = new HashMap<>();
        while (reader.hasNext()) {
            Record r = reader.next();
            if (!comp) {
                continue;
            }
            String c_name = r.get("c_name").toString();
            long c_ck = (long) r.get("c_custkey");
            @SuppressWarnings("unchecked")
            List<Record> orders = (List<Record>) r.get(offsetOrders);
            for (Record order : orders) {
                long o_ok = (long) order.get("o_orderkey");
                String o_od = order.get("o_orderdate").toString();
                float o_tp = (float) order.get("o_totalprice");
                @SuppressWarnings("unchecked")
                List<Record> lines = (List<Record>) order.get(offsetLines);
                float sumqlt = .0f;
                for (Record line : lines) {
                    sumqlt += (float) line.get("l_quantity");
                }
                if (sumqlt > 300) {
                    results.put(c_name + "|" + c_ck + "|" + o_ok + "|" + o_od + "|" + o_tp, sumqlt);
                    count++;
                }
            }
        }
        result = result / sum * 100;
        long end = System.currentTimeMillis();
        System.out.println(results.size());
        /*for (Byte d : flag) {
            System.out.println(new String(new byte[] { d }));
        }*/
        System.out.println(count);
        System.out.println("NCFile time: " + (end - start) + " result: " + result + " ios: "
                + reader.getBlockManager().getTotalRead() + " aiotime: "
                + reader.getBlockManager().getAioTime() / 1000000 + " aioFetchtime: "
                + reader.getBlockManager().getAioFetchTime() / 1000000 + " iotime: "
                + reader.getBlockManager().getTotalTime() / 1000000 + " compressiontime: "
                + reader.getBlockManager().getCompressionTime() / 1000000 + " created: "
                + reader.getBlockManager().getCreated() + " read: "
                + reader.getBlockManager().getReadLength() / reader.getBlockManager().getTotalRead());
        reader.close();
        java.text.NumberFormat nf = java.text.NumberFormat.getInstance();
        nf.setGroupingUsed(false);
        System.out.println("revenue: " + nf.format(result));
    }

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 5) {
            System.out.println("Command: file schema max command blocksize");
            System.exit(-1);
        }

        switch (args[3]) {
            case "q01":
                Q01_FilteringScan(args);
                break;
            case "q02":
                Q02_FilteringScan(args);
                break;
            case "q03":
                Q03_FilteringScan(args);
                break;
            case "q04":
                Q04_FilteringScan(args);
                break;
            case "q05":
                Q05_FilteringScan(args);
                break;
            case "q06":
                Q06_FilteringScan(args);
                break;
            case "q07":
                Q07_FilteringScan(args);
                break;
            case "q10":
                Q10_FilteringScan(args);
                break;
            case "q15":
                Q15_FilteringScan(args);
                break;
            case "q81":
                Q81_FilteringScan(args);
                break;
            default:
                break;
        }
    }
}
