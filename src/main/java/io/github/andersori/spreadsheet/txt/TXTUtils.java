package io.github.andersori.spreadsheet.txt;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import org.apache.commons.io.FilenameUtils;
import org.apache.tika.io.IOUtils;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

public class TXTUtils {

  public static Mono<Resource> replace_UTF_8(
      FilePart file, String oldCharacter, String newCharacter) {
    return replace(file, oldCharacter, newCharacter, Charset.forName("UTF-8"));
  }

  public static Mono<Resource> replace_ISO_8859_1(
      FilePart file, String oldCharacter, String newCharacter) {
    return replace(file, oldCharacter, newCharacter, Charset.forName("ISO-8859-1"));
  }

  public static Mono<Resource> replace(
      FilePart file, String oldCharacter, String newCharacter, Charset cs) {
    if (!(FilenameUtils.getExtension(file.filename()).equalsIgnoreCase("txt"))) {
      return Mono.error(
          new ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é permitido a leitura de TXT."));
    }
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMap(
            input ->
                Mono.fromCallable(
                    () ->
                        IOUtils.readLines(input, cs.name()).stream()
                            .map(line -> line.replace(oldCharacter, newCharacter))
                            .reduce(
                                "",
                                (a, b) -> {
                                  if (a.isBlank()) {
                                    return b;
                                  }
                                  return a + "\n" + b;
                                })))
        .flatMap(
            text ->
                Mono.fromCallable(
                    () -> {
                      ByteArrayOutputStream inMemory = new ByteArrayOutputStream();
                      BufferedWriter inMemoryStream =
                          new BufferedWriter(new OutputStreamWriter(inMemory, cs));

                      inMemoryStream.write(new String(text.getBytes(), cs));
                      inMemoryStream.flush();

                      return new ByteArrayResource(inMemory.toByteArray());
                    }));
  }
}
