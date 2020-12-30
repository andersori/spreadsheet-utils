package io.github.andersori.spreadsheet.txt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.nio.charset.Charset;
import java.util.function.Function;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

public class TXTUtils {

  private static Function<InputStream, Mono<Resource>> replace(
      String oldCharacter, String newCharacter, Charset charset) {
    return input ->
        Mono.fromCallable(
                () ->
                    new BufferedReader(new InputStreamReader(input, charset))
                        .lines()
                        .map(line -> line.replace(oldCharacter, newCharacter))
                        //                        .peek(csv -> System.out.println(csv.toString()))
                        .reduce(
                            "",
                            (a, b) -> {
                              if (a.isBlank()) {
                                return b;
                              }
                              return a + "\n" + b;
                            }))
            .flatMap(text -> Mono.fromCallable(() -> new ByteArrayResource(text.getBytes())));
  }

  public static Mono<Resource> replace(
      FilePart file, String oldCharacter, String newCharacter, Charset charset) {
    return file.content()
        .map(DataBuffer::asInputStream)
        .reduce(SequenceInputStream::new)
        .flatMap(replace(oldCharacter, newCharacter, charset));
  }

  public static Mono<Resource> replace(
      Resource file, String oldCharacter, String newCharacter, Charset charset) {
    return Mono.fromCallable(() -> file.getInputStream())
        .flatMap(replace(oldCharacter, newCharacter, charset));
  }
}
