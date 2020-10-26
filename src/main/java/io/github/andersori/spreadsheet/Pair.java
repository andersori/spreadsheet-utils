package io.github.andersori.spreadsheet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public final class Pair<S, T> {
	private final @NonNull S first;
	private final @NonNull T second;
	
	public static <S, T> Pair<S, T> of(S first, T second) {
		return new Pair<>(first, second);
	}
}
