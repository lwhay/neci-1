package neci.nest.ppsl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import neci.ncfile.BatchAvroColumnWriter;
import neci.ncfile.ComparableKey;
import neci.ncfile.NestManager;
import neci.ncfile.SortedAvroReader;
import neci.ncfile.SortedAvroWriter;
import neci.ncfile.base.Schema;
import neci.ncfile.base.Schema.Field;
import neci.ncfile.generic.GenericData.Record;

public class NCFile_PPSL_Codec {
    private static String codec;
    private static int blockSize;

    static void lSort(String path, String schema, int[] fields, String resultPath, int free, int mul)
            throws IOException {
        Schema l = new Schema.Parser().parse(new File(schema));
        List<Field> fs = l.getFields();
        SortedAvroWriter<ComparableKey, Record> writer =
                new SortedAvroWriter<ComparableKey, Record>(resultPath, l, free, mul);
        BufferedReader reader = new BufferedReader(new FileReader(new File(path)));
        String line;
        while ((line = reader.readLine()) != null) {
            String[] tmp = line.split("\\|");
            Record data = new Record(l);
            for (int i = 0; i < fs.size(); i++) {
                switch (fs.get(i).schema().getType()) {
                    case INT:
                        data.put(i, Integer.parseInt(tmp[i]));
                        break;
                    case LONG:
                        data.put(i, Long.parseLong(tmp[i]));
                        break;
                    case FLOAT:
                        data.put(i, Float.parseFloat(tmp[i]));
                        break;
                    case DOUBLE:
                        data.put(i, Double.parseDouble(tmp[i]));
                        break;
                    case BYTES:
                        data.put(i, ByteBuffer.wrap(tmp[i].getBytes()));
                        break;
                    default:
                        data.put(i, tmp[i]);
                }
            }
            writer.append(new ComparableKey(data, fields), data);
        }
        reader.close();
        writer.flush();
        System.gc();
    }

    static void doublePri(String path1, String path2, String schema1, String schema2, int[] fIn1, int[] fIn2,
            int[] fOut, String resultPath, int free, int mul) throws IOException {
        Schema s1 = new Schema.Parser().parse(new File(schema1 + "single.avsc"));
        Schema s = new Schema.Parser().parse(new File(schema1 + "nest.avsc"));
        Schema s2 = new Schema.Parser().parse(new File(schema2));
        List<Field> fs1 = s1.getFields();

        SortedAvroReader reader1 = new SortedAvroReader(path1, s1, fIn1);
        SortedAvroReader reader2 = new SortedAvroReader(path2, s2, fIn2);

        SortedAvroWriter<ComparableKey, Record> writer =
                new SortedAvroWriter<ComparableKey, Record>(resultPath, s, free, mul);

        Record r2 = reader2.next();
        ComparableKey k2 = new ComparableKey(r2, fIn2);
        while (reader1.hasNext()) {
            Record r1 = reader1.next();
            Record data = new Record(s);
            for (int i = 0; i < fs1.size(); i++) {
                data.put(i, r1.get(i));
            }
            ComparableKey k1 = new ComparableKey(data, fIn1);

            while (k2 != null && k1.compareTo(k2) > 0) {
                Record dataL = new Record(s);
                for (int i = 0; i < fIn1.length; i++) {
                    dataL.put(fIn1[i], r2.get(fIn2[i]));
                }

                List<Record> arrL = new ArrayList<Record>();
                arrL.add(r2);
                ComparableKey newK2;
                if (reader2.hasNext()) {
                    r2 = reader2.next();
                    newK2 = new ComparableKey(r2, fIn2);
                } else {
                    newK2 = null;
                    reader2.close();
                    //                    break;
                }
                while (newK2.equals(k2)) {
                    arrL.add(r2);
                    if (reader2.hasNext()) {
                        r2 = reader2.next();
                        newK2 = new ComparableKey(r2, fIn2);
                    } else {
                        newK2 = null;
                        reader2.close();
                        break;
                    }
                }
                dataL.put(fs1.size(), arrL);
                writer.append(k2, dataL);
                k2 = newK2;
            }
            List<Record> arr = new ArrayList<Record>();
            while (k2 != null && k1.compareTo(k2) == 0) {
                arr.add(r2);
                if (reader2.hasNext()) {
                    r2 = reader2.next();
                    k2 = new ComparableKey(r2, fIn2);
                } else {
                    k2 = null;
                    reader2.close();
                    break;
                }
            }
            data.put(fs1.size(), arr);
            writer.append(new ComparableKey(data, fOut), data);
        }
        reader1.close();
        NestManager.shDelete(path2);
        System.out.println("Deleted " + path2);

        while (k2 != null) {
            Record dataL = new Record(s);
            for (int i = 0; i < fIn1.length; i++) {
                dataL.put(fIn1[i], r2.get(fIn2[i]));
            }

            List<Record> arrL = new ArrayList<Record>();
            arrL.add(r2);
            ComparableKey newK2;
            if (reader2.hasNext()) {
                r2 = reader2.next();
                newK2 = new ComparableKey(r2, fIn2);
            } else {
                newK2 = null;
                reader2.close();
                //                    break;
            }
            while (newK2.equals(k2)) {
                arrL.add(r2);
                if (reader2.hasNext()) {
                    r2 = reader2.next();
                    newK2 = new ComparableKey(r2, fIn2);
                } else {
                    newK2 = null;
                    reader2.close();
                    break;
                }
            }
            dataL.put(fs1.size(), arrL);
            writer.append(k2, dataL);
            k2 = newK2;
        }

        writer.flush();
        System.gc();
        NestManager.shDelete(path1);
        System.out.println("Deleted " + path1);
    }

    static int finalTran(String path1, String path2, String schema1, String schema2, int[] fIn1, int[] fIn2,
            String resultPath, int free, int mul) throws IOException {
        int x = 0;

        Schema s1 = new Schema.Parser().parse(new File(schema1 + "single.avsc"));
        Schema s = new Schema.Parser().parse(new File(schema1 + "nest.avsc"));
        Schema s2 = new Schema.Parser().parse(new File(schema2));
        List<Field> fs1 = s1.getFields();

        BufferedReader reader1 = new BufferedReader(new FileReader(new File(path1)));
        SortedAvroReader reader2 = new SortedAvroReader(path2, s2, fIn2);

        BatchAvroColumnWriter<Record> writer =
                new BatchAvroColumnWriter<Record>(s, resultPath, free, mul, blockSize, codec);

        String line;
        Record r2 = reader2.next();
        ComparableKey k2 = new ComparableKey(r2, fIn2);
        //        int count = 0;
        while ((line = reader1.readLine()) != null) {
            String[] tmp = line.split("\\|");
            Record data = new Record(s);
            for (int i = 0; i < fs1.size(); i++) {
                switch (fs1.get(i).schema().getType()) {
                    case INT:
                        data.put(i, Integer.parseInt(tmp[i]));
                        break;
                    case LONG:
                        data.put(i, Long.parseLong(tmp[i]));
                        break;
                    case FLOAT:
                        data.put(i, Float.parseFloat(tmp[i]));
                        break;
                    case DOUBLE:
                        data.put(i, Double.parseDouble(tmp[i]));
                        break;
                    case BYTES:
                        data.put(i, ByteBuffer.wrap(tmp[i].getBytes()));
                        break;
                    default:
                        data.put(i, tmp[i]);
                }
            }
            ComparableKey k1 = new ComparableKey(data, fIn1);

            while (k2 != null && k1.compareTo(k2) > 0) {
                if (reader2.hasNext()) {
                    r2 = reader2.next();
                    k2 = new ComparableKey(r2, fIn2);
                } else {
                    k2 = null;
                    reader2.close();
                    break;
                }
            }
            List<Record> arr = new ArrayList<Record>();
            while (k2 != null && k1.compareTo(k2) == 0) {
                if (r2.get(2) == null) {
                    x++;
                    r2.put(0, null);
                    r2.put(1, null);
                }
                arr.add(r2);
                if (reader2.hasNext()) {
                    r2 = reader2.next();
                    k2 = new ComparableKey(r2, fIn2);
                } else {
                    k2 = null;
                    reader2.close();
                    break;
                }
            }
            data.put(fs1.size(), arr);
            writer.flush(data);
            //            count++;
            //            if (count >= 20)
            //                break;
        }
        reader1.close();
        reader2.close();
        int index = writer.flush();
        System.out.println("########################the null ps number: " + x);
        NestManager.shDelete(path2);
        System.out.println("Deleted " + path2);
        return index;
        //        File[] files = new File[index];
        //        for (int i = 0; i < index; i++)
        //            files[i] = new File(resultPath + "file" + String.valueOf(i) + ".trv");
        //        if (index == 1) {
        //            new File(resultPath + "file0.head").renameTo(new File(resultPath + "result.head"));
        //            new File(resultPath + "file0.neci").renameTo(new File(resultPath + "result.neci"));
        //        } else {
        //            writer.mergeFiles(files);
        //        }
    }

    public static void main(String[] args) throws IOException {
        String path = args[0];
        String result = args[1] + "result";
        String schema = args[1] + "lay";
        int free = Integer.parseInt(args[2]);
        int mul = Integer.parseInt(args[3]);
        int max = Integer.parseInt(args[4]);
        codec = args[5];
        blockSize = Integer.parseInt(args[6]);

        int[] fields0 = new int[] { 1, 2, 0, 3 };
        long start = System.currentTimeMillis();
        lSort(path + "lineitem.tbl", schema + "1/single.avsc", fields0, result + "1/", free, mul);
        long end = System.currentTimeMillis();
        System.out.println("+++++++lineitem sort time+++++++" + (end - start));

        int[] fields1 = new int[] { 0, 1 };
        int[] fields2 = new int[] { 1, 2 };
        int[] fields3 = new int[] { 0, 1 };

        start = System.currentTimeMillis();
        lSort(path + "partsupp.tbl", schema + "2/single.avsc", fields1, result + "2/", free, mul);
        end = System.currentTimeMillis();
        System.out.println("+++++++partsupp sort time+++++++" + (end - start));

        start = System.currentTimeMillis();
        doublePri(result + "2/", result + "1/", schema + "2/", schema + "1/single.avsc", fields1, fields2, fields3,
                result + "3/", free, mul);
        end = System.currentTimeMillis();
        System.out.println("+++++++partsupp&&lineitem time+++++++" + (end - start));

        int[] fields4 = new int[] { 0 };
        int[] fields5 = new int[] { 0 };
        start = System.currentTimeMillis();
        @SuppressWarnings("unused")
        int index = finalTran(path + "part.tbl", result + "3/", schema + "3/", schema + "2/nest.avsc", fields4, fields5,
                result + "/", max, mul);
        end = System.currentTimeMillis();
        System.out.println("+++++++part&&partsupp&&lineitem time+++++++" + (end - start));

        /*String resultPath = result + "/";
        Schema s = new Schema.Parser().parse(new File(schema + "3/" + "nest.avsc"));
        BatchAvroColumnWriter<Record> writer =
                new BatchAvroColumnWriter<Record>(s, resultPath, max, mul, blockSize, codec);
        File[] files = new File[index];
        for (int i = 0; i < index; i++)
            files[i] = new File(resultPath + "file" + String.valueOf(i) + ".neci");
        writer.mergeFiles(files);
        System.out.println("merge completed!");*/
    }
}
