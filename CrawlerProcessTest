package dm.data_load.company.pl.tests.sources.pl_eusubsidies;

import com.deltavista.rep.taskprocessor.loc.ConcurrentTaskProcessorExtended;
import com.deltavista.rep.taskprocessor.loc.ConcurrentTaskProcessorExtendedBuilder;
import junit.framework.TestCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static dm.data_load.company.pl.subsidies.AutomatizationSubsidiesProcessTask.*;

public class AutomatizationSubsidiesProcessTaskTest extends TestCase {
  private static final String PROCESS_CONFIG_PATH
          = "classpath:dm-data-load-company/configs/task_processor/subsidies/tp_subsidies_file_crawler.xml";

  @SuppressWarnings("WeakerAccess")
  @Test
  @DisplayName("Test whole process performing...")
  public void testWholeProcess() throws IOException {
    ConcurrentTaskProcessorExtended executor = new ConcurrentTaskProcessorExtendedBuilder("DL_COMPANY_PL", PROCESS_CONFIG_PATH).build();
    executor.execute();
    //Check if downloaded file exist and its length is more than zero
    final String file_from_context = executor.getContext().get(FILE1_EXIST);
    if(file_from_context != null){
      File file = new File(file_from_context);
      assertTrue(file.exists());
      assertTrue(file.length() > 0);
      assertTrue(file.canRead());
      //Check if unzipped directory is created correctly
      assertFalse(isDirEmpty(Paths.get(executor.getContext().get(DIR_EXIST))));
    }
    //Check if split file exist
    final String split_file_from_context = executor.getContext().get(SPLIT_FILE);
    if(split_file_from_context != null){
      File splitFile = new File(split_file_from_context);
      assertTrue(splitFile.exists());
      assertTrue(splitFile.length() > 0);
      assertTrue(splitFile.canRead());
    }
  }

  /**
   * @param directory path to directory.
   * @return true if directory is empty.
   * @throws IOException if an I/O error occurs.
   */
  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }
}
