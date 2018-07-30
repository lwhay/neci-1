/**
 * 
 */
package neci.parallel;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import neci.ncfile.BatchAvroColumnWriter;
import neci.ncfile.BatchColumnReader;
import neci.ncfile.NestManager;
import neci.ncfile.base.Schema;
import neci.ncfile.generic.GenericData.Record;
import neci.parallel.worker.MergeThread;

/**
 * @author Michael
 *
 */
public class MultiThreadMergeTest {
    public static void create(String[] args) throws IOException {
        NestManager.shDelete(args[1]);
        NestManager.shDelete(args[2]);
        Schema schema = new Schema.Parser().parse(new File(args[0]));
        BatchAvroColumnWriter<Record> writer = new BatchAvroColumnWriter<>(schema, args[1], 10, 10, 1, "null");
        for (int i = 0; i < 10000; i++) {
            Record r1 = new Record(schema);
            r1.put(0, i);
            List<Record> f1 = new ArrayList<>();
            Record r11 = new Record(schema.getFields().get(1).schema().getElementType());
            r11.put(0, i);
            Record r12 = new Record(
                    schema.getFields().get(1).schema().getElementType().getFields().get(1).schema().getElementType());
            r12.put(0, i);
            List<Record> f2 = new ArrayList<>();
            f2.add(r12);
            r11.put(1, f2);
            f1.add(r11);
            r1.put(1, f1);
            System.out.println(r1);
            writer.flush(r1);
        }
        writer.flush();
    }

    public static void scan(String[] args) throws IOException {
        for (int i = 0; i < Integer.parseInt(args[5]); i++) {
            System.out.println("Openning file: " + args[2] + i + "/result.neci");
            Schema schema = new Schema.Parser().parse(new File(args[0]));
            BatchColumnReader<Record> reader = new BatchColumnReader<>(new File(args[2] + i + "/result.neci"), 1);
            reader.createSchema(schema);
            reader.create();
            while (reader.hasNext()) {
                Record record = reader.next();
                System.out.println(record);
            }
            reader.close();
        }
    }

    public static void clear(String[] args) throws IOException {
        NestManager.shDelete(args[1]);
        NestManager.shDelete(args[2]);
    }

    /**
     * @param args
     * @throws InterruptedException
     * @throws IOException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    public static void main(String[] args)
            throws IOException, InterruptedException, InstantiationException, IllegalAccessException {
        create(args);
        if (args.length != 9) {
            System.out.println(
                    "Command: String sPath, String dPath, String tPath, int wc, int mul, int dg, int gran, String codec, int blockSize");
            System.exit(0);
        }
        MultiThreadMerge<MergeThread> builder = new MultiThreadMerge<>(MergeThread.class, args[0], args[1], args[2],
                Integer.parseInt(args[8]), Integer.parseInt(args[3]), Integer.parseInt(args[4]),
                Integer.parseInt(args[5]), Integer.parseInt(args[6]), args[7]);
        long begin = System.currentTimeMillis();
        builder.build();
        System.out.println("Merge elipse: " + (System.currentTimeMillis() - begin));
        scan(args);
        //clear(args);
    }

}
