package dm.data_load.company.pl.subsidies;

import com.deltavista.data.persistence.dm.dao.CrawlingBlacklistedEntryFacade;
import com.deltavista.data.persistence.dm.dto.gen.CrawlingBlacklistedEntry;
import com.deltavista.dm.utils.SpringUtil;
import com.deltavista.rep.taskprocessor.AbstractTask;
import com.deltavista.rep.taskprocessor.BaseTaskProgressInfo;
import lombok.Getter;
import org.apache.commons.compress.archivers.jar.JarArchiveEntry;
import org.apache.commons.compress.archivers.jar.JarArchiveInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AutomatizationSubsidiesProcessTask extends AbstractTask {
  private static final CrawlingBlacklistedEntryFacade crawlingBlacklistedEntryFacade = SpringUtil.getBean(CrawlingBlacklistedEntryFacade.class);
  private static final Logger LOGGER = Logger.getLogger(AutomatizationSubsidiesProcessTask.class.getName());
  private static final String BLACKLIST_TYPE = "file_name";

  @SuppressWarnings("WeakerAccess")
  public static final String DOWNLOADED_FILE_DIR = "downloaded_file_dir";
  @SuppressWarnings("WeakerAccess")
  public static final String PARSED_SITE = "parsed_site";
  @SuppressWarnings("WeakerAccess")
  public static final String PROXY = "proxy";
  @SuppressWarnings("WeakerAccess")
  public static final String PROXY_PORT = "proxy_port";
  @SuppressWarnings("WeakerAccess")
  public static final String SOURCE_ID = "source_id";
  private static final String COULD_NOT_CREATE_DIRECTORY = "Could not create directory: ";

  private ThisTaskProperties config;
  public static final String FILE1_EXIST = "ctx_output_file_z1";
  public static final String DIR_EXIST = "ctx_output_dir";
  public static final String SPLIT_FILE = "ctx_output_split_file";

  /**
   * Run the whole process.
   */
  @Override
  public void execute() {
    createAndValidateConfig();
    notify(new BaseTaskProgressInfo(this, "Task config [" + config + "]"));

    List<String> linksToProcess = selectNotProcessedLinks(parseLinksToDownload());
    linksToProcess.parallelStream().forEach(url -> downloadFile(config.getDownloadedFileDir(),
            url.substring(url.lastIndexOf('/') + 1), url));

    linksToProcess
            .forEach(url -> {
              try {
                decompress(config.getDownloadedFileDir() +
                        url.substring(url.lastIndexOf('/') + 1), config.getDownloadedFileDir()
                        + "unzipped");
              } catch (IOException exception) {
                LOGGER.log(Level.INFO, exception.getMessage());
              }
            });

    prepareExcelFileToProcessing(config.getDownloadedFileDir() + "unzipped");
  }

  @Override
  public void checkDependencies() {
    createAndValidateConfig();
  }

  /**
   * Prepare initialized task configuration.
   */
  private void createAndValidateConfig() {
    config = new ThisTaskProperties();
  }

  /**
   * Choose not processed urls.
   *
   * @param urls List of parsed urls.
   */
  private List<String> selectNotProcessedLinks(List<String> urls) {
    return urls.parallelStream().filter(url -> !crawlingBlacklistedEntryFacade.isBlacklisted(Integer.valueOf(config.getSourceId()), BLACKLIST_TYPE, trimUrlToFileName(url)))
            .collect(Collectors.toList());
  }

  /**
   * Trim url to file name.
   *
   * @param url URL of downloaded file.
   */
  private static String trimUrlToFileName(String url) {
    return url.substring(url.lastIndexOf('/') + 1);
  }

  /**
   * Download a file for specified url and save it to the file.
   *
   * @param dir      File path.
   * @param fileName Name of the file to save.
   * @param url      Url to download.
   */
  private void downloadFile(final String dir, final String fileName, final String url) {
    File directory = new File(dir);
    if (!directory.exists() && !directory.mkdirs()) {
      LOGGER.log(Level.INFO, COULD_NOT_CREATE_DIRECTORY + directory.getPath());
    }
    try (FileOutputStream fOutStream = new FileOutputStream(dir + fileName)) {
      URL urlObj = new URL(url);
      SocketAddress address = new InetSocketAddress(config.getProxy(), config.getProxyPort());
      Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
      try (ReadableByteChannel readableBC = Channels.newChannel(urlObj.openConnection(proxy).getInputStream())) {
        fOutStream.getChannel().transferFrom(readableBC, 0, Long.MAX_VALUE);
      }
      getContext().put(FILE1_EXIST, dir + fileName);
      insertFileToBlackList(fileName);
    } catch (IOException exception) {
      LOGGER.log(Level.INFO, exception.getMessage());
    }
  }

  /**
   * Insert name files to BlackList database.
   *
   * @param fileName Name of the downloaded file.
   */
  private void insertFileToBlackList(final String fileName) {
    CrawlingBlacklistedEntry entry = new CrawlingBlacklistedEntry();
    entry.setSrcId(Integer.valueOf(config.getSourceId()));
    entry.setType(BLACKLIST_TYPE);
    entry.setValue(fileName);
    crawlingBlacklistedEntryFacade.insertCrawlingBlacklistedEntry(entry);
  }

  /**
   * Get a url of website and parse links to download.
   *
   * @return List of links to download.
   */
  private List<String> parseLinksToDownload() {
    SocketAddress address = new InetSocketAddress(config.getProxy(), config.getProxyPort());
    Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
    Elements elements;
    try {
      Document doc = Jsoup.connect(config.getParsedSite()).proxy(proxy).get();
      elements = doc.getElementsByClass("resource-url-analytics");

      return elements.parallelStream().map(element -> element.select("a[href$=zip]").attr("href")).filter(element -> !element.isEmpty()).collect(Collectors.toList());
    } catch (IOException exception) {
      LOGGER.log(Level.INFO, exception.getMessage());
      return new ArrayList<>();
    }
  }

  /**
   * Unzip the specified file.
   *
   * @param zipFilePath    The path to the zip file.
   * @param destinationDir The path in which zip file will be unzipped.
   */
  private void decompress(String zipFilePath, String destinationDir) throws IOException {
    try (JarArchiveInputStream jin = new JarArchiveInputStream(new FileInputStream(zipFilePath))) {
      JarArchiveEntry entry;
      while ((entry = jin.getNextJarEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }

        String entryName = entry.getName().contains("/")
                ? entry.getName().substring(entry.getName().indexOf('/') + 1) : entry.getName();

        getContext().put(DIR_EXIST, destinationDir);

        File currentFile = new File(destinationDir, entryName);
        File parent = currentFile.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
          LOGGER.log(Level.INFO, COULD_NOT_CREATE_DIRECTORY + parent.getPath());
        }
        IOUtils.copy(jin, new FileOutputStream(destinationDir + "\\" + entryName));
      }
    }
  }

  /**
   * Create new xls files based on sheets from files from pathToUnzippedFolder.
   *
   * @param pathToUnzippedFolder Path to unzipped excel files.
   */
  private void prepareExcelFileToProcessing(String pathToUnzippedFolder) {
    File unzippedDir = new File(pathToUnzippedFolder);
    File[] excelFiles = unzippedDir.listFiles();
    if (excelFiles != null) {
      for (File excelFile : excelFiles) {
        splittingExcels(pathToUnzippedFolder, excelFile);
      }
    }
  }

  /**
   * Splitting sheets from excelFile.
   *
   * @param pathToUnzippedFolder Path to unzipped excel files.
   * @param excelFile            Processing excel file.
   */
  private void splittingExcels(String pathToUnzippedFolder, File excelFile) {
    //Prepare as many files as many sheets exist
    try (Workbook wb = new HSSFWorkbook(new FileInputStream(excelFile))) {
      for (int i = 0; i < wb.getNumberOfSheets() - 1; i++) {
        File originalWb = new File(String.valueOf(excelFile));

        File directory = new File(pathToUnzippedFolder + "/readyToProcess");
        if (!directory.exists() && !directory.mkdirs()) {
          LOGGER.log(Level.INFO, COULD_NOT_CREATE_DIRECTORY + directory.getPath());
        }

        final String newExcelNames = pathToUnzippedFolder + "/readyToProcess" + "\\" + excelFile.getName().replace(".xls", "") + "_z" + (i + 1) + ".xls";
        File clonedWb = new File(newExcelNames);
        Files.copy(originalWb.toPath(), clonedWb.toPath());

        //Delete unnecessary sheets
        try (Workbook newWb = new HSSFWorkbook(new FileInputStream(newExcelNames))) {
          for (int j = 0; j < newWb.getNumberOfSheets() - 1; j++) {
            if (i != j) {
              newWb.removeSheetAt(j);
            }
          }
          newWb.write(new FileOutputStream(newExcelNames));
          getContext().put(SPLIT_FILE, newExcelNames);
        }
      }
    } catch (IOException exception) {
      LOGGER.log(Level.INFO, exception.getMessage());
    }
  }

  /**
   * Class stores properties for subsidies process.
   */
  private class ThisTaskProperties {
    @Getter
    private final String downloadedFileDir;
    @Getter
    private final int proxyPort;
    @Getter
    private final String proxy;
    @Getter
    private final String parsedSite;
    @Getter
    private final String sourceId;

    private ThisTaskProperties() {
      final Map<String, String> parameters = getBoundInputParameters();
      downloadedFileDir = getRequiredInputParameter(parameters, DOWNLOADED_FILE_DIR);
      proxyPort = Integer.valueOf(getRequiredInputParameter(parameters, PROXY_PORT));
      proxy = getRequiredInputParameter(parameters, PROXY);
      parsedSite = getRequiredInputParameter(parameters, PARSED_SITE);
      sourceId = getRequiredInputParameter(parameters, SOURCE_ID);
    }

    @Override
    public String toString() {
      return "ThisTaskProperties{" +
              "downloadedFileDir='" + downloadedFileDir + '\'' +
              ", proxyPort=" + proxyPort +
              ", proxy='" + proxy + '\'' +
              ", parsedSite='" + parsedSite + '\'' +
              ", sourceId='" + sourceId + '\'' +
              '}';
    }
  }
}
