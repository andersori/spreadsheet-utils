package io.github.andersori.spreadsheet.csv;

import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.io.IOUtils;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CSVUtils {

  public static <T> List<T> read(MultipartFile file, Class<T> clazz) {
    if (!(FilenameUtils.getExtension(file.getOriginalFilename()).equalsIgnoreCase("csv"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de CSV.");
    }

    try {
      return read(file.getBytes(), clazz);
    } catch (IOException e) {
      throw new RuntimeException("Erro na leitura do CSV", e);
    }
  }

  public static <T> Flux<T> read(FilePart file, Class<T> clazz) {

    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("csv"))) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido o envio de CSV.");
    }

    return file.content()
        .map(buffer -> buffer.asInputStream())
        .reduce(SequenceInputStream::new)
        .flatMapMany(
            input -> {
              try {
                return Flux.fromIterable(read(IOUtils.toByteArray(input), clazz));
              } catch (IOException e) {
                return Flux.error(e);
              }
            });
  }

  private static <T> List<T> read(byte[] bytes, Class<T> clazz) {
    List<T> response = new ArrayList<>();

    CharsetDetector detector = new CharsetDetector().setText(bytes);
    List<CharsetMatch> allOtherscharsets = Arrays.asList(detector.detectAll());
    CharsetMatch bestCharset = detector.detect();

    String[] charsetsSupported = CharsetDetector.getAllDetectableCharsets();

    CsvToBeanBuilder<T> csvBuilder = null;
    if (Stream.of(charsetsSupported)
        .filter(charset -> charset.equalsIgnoreCase(bestCharset.getName()))
        .findFirst()
        .isPresent()) {
      /*UTILIANDO O MELHOR CHARSET*/
      csvBuilder = new CsvToBeanBuilder<>(bestCharset.getReader());
    } else if (Stream.of(charsetsSupported)
        .filter(charset -> charset.equalsIgnoreCase(allOtherscharsets.get(1).getName()))
        .findFirst()
        .isPresent()) {
      /*UTILIANDO O SEGUNDO MELHOR CHARSET*/
      csvBuilder = new CsvToBeanBuilder<>(allOtherscharsets.get(1).getReader());
    } else {
      throw new RuntimeException("Salve seu arquivo como UTF-8 ou ISO-8859-1 e envie novamente");
    }

    HeaderColumnNameMappingStrategy<T> strategy = new HeaderColumnNameMappingStrategy<T>();
    strategy.setType(clazz);

    response.addAll(
        csvBuilder
            .withMappingStrategy(strategy)
            .withType(clazz)
            .withSeparator(';')
            .withIgnoreLeadingWhiteSpace(true)
            .build()
            .parse());

    return response;
  }

  public static <Bean> Mono<Resource> write(Class<Bean> clazz, List<Bean> list, Charset chatset) {
    File file = null;
    try {
      file = File.createTempFile(UUID.randomUUID().toString(), ".csv");

      FileOutputStream fos = new FileOutputStream(file.getCanonicalFile());
      Writer writer = new OutputStreamWriter(fos, chatset);

      try {
        HeaderColumnNameMappingStrategy<Bean> strategy =
            new HeaderColumnNameMappingStrategy<Bean>();
        strategy.setType(clazz);

        StatefulBeanToCsv<Bean> sbc =
            new StatefulBeanToCsvBuilder<Bean>(writer)
                .withMappingStrategy(strategy)
                .withSeparator(';')
                .build();

        sbc.write(list);
      } catch (CsvDataTypeMismatchException | CsvRequiredFieldEmptyException e) {
        file.delete();
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST, "Error ao salvar dados de exemplo no CSV.", e);
      } finally {
        writer.close();
      }
      Mono<Resource> res = Mono.just(new ByteArrayResource(Files.readAllBytes(file.toPath())));
      file.delete();
      return res;
    } catch (IOException e) {
      if (file != null) {
        file.delete();
      }
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Erro ao criar arquivo temporario para gerar um exemplo de CSV.",
          e);
    }
  }
}
