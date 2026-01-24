package tanin.backdoor.core;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class CsvWriter implements AutoCloseable {

  FileWriter fileWriter;
  BufferedWriter bufferedWriter;
  boolean startOfTheLine = true;

  public CsvWriter(String filePath) throws IOException {
    fileWriter = new java.io.FileWriter(filePath);
    bufferedWriter = new java.io.BufferedWriter(fileWriter);
  }

  public void addValue(String value) throws IOException {
    if (startOfTheLine) {
      startOfTheLine = false;
    } else {
      bufferedWriter.write(',');
    }

    if (value == null || value.isEmpty()) {
      bufferedWriter.write("");
    } else {
      bufferedWriter.write('"' + value.replace("\"", "\"\"") + '"');
    }
  }

  public void newLine() throws IOException {
    bufferedWriter.newLine();
    startOfTheLine = true;
  }

  public void flush() throws IOException {
    bufferedWriter.flush();
  }

  @Override
  public void close() throws Exception {
    bufferedWriter.close();
    fileWriter.close();
  }
}
