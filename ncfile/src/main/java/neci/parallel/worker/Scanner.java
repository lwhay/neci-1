/**
 * 
 */
package neci.parallel.worker;

import neci.ncfile.base.Schema;

/**
 * @author Michael
 *
 */
public abstract class Scanner implements Runnable {

    public abstract void init(Schema schema, String path, int batchSize, int blockSize);

    public abstract void run();
}
