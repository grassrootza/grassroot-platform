package za.org.grassroot.language;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CharStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * Extends an ordinary ANTLRInputStream to convert all characters to lower case
 * @author Joe Stelmach 
 *
 */
public class ANTLRNoCaseInputStream extends ANTLRInputStream {
  public ANTLRNoCaseInputStream(InputStream inputStream) throws IOException {
    super(inputStream, 0);
  }

  @Override
  public int LA(int i) {
    if (i == 0) return 0;
    if (i < 0) i++;
    if ((p + i - 1) >= n) return CharStream.EOF;
    return Character.toLowerCase(data[p + i - 1]);
  }
}