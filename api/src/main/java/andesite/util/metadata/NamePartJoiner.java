package andesite.util.metadata;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Joins a list of strings into a string, possibly transforming
 * them or adding separators.
 */
public interface NamePartJoiner {
    /**
     * Joins the strings with a dash ({@code -}) character.
     *
     * <br>{@code [some, strings, go, here]} would become {@code some-strings-go-here}.
     */
    NamePartJoiner DASHED = joinWith("-");
    
    /**
     * Joins the strings with a dash ({@code -}) character, after converting them to
     * Title Case.
     *
     * <br>{@code [some, strings, go, here]} would become {@code Some-Strings-Go-Here}.
     */
    NamePartJoiner HTTP_HEADER = DASHED.afterMapping(NamePartJoiner::toTitleCase);
    
    /**
     * Joins the strings after converting all but the first into Title Case.
     *
     * <br>{@code [some, strings, go, here]} would become {@code someStringsGoHere}.
     */
    NamePartJoiner LOWER_CAMEL_CASE = parts -> {
        StringJoiner joiner = new StringJoiner("");
        for(var it = parts.listIterator(); it.hasNext(); ) {
            if(!it.hasPrevious()) {
                joiner.add(it.next());
            } else {
                joiner.add(toTitleCase(it.next()));
            }
        }
        return joiner.toString();
    };
    
    /**
     * Joins the provided parts into a string. The joiner is
     * free to apply any transformation it wants to the strings,
     * and join them however it wants.
     *
     * @param parts Parts to join. All strings in this list should
     *              be in lower case.
     *
     * @return The resulting string.
     */
    @Nonnull
    @CheckReturnValue
    String join(@Nonnull List<String> parts);
    
    /**
     * Returns a new joiner that will join the strings resulting
     * from applying the provided function to each part.
     *
     * @param mapper Maps the parts before joining.
     *
     * @return A joiner that maps the parts before calling the join method.
     */
    @Nonnull
    @CheckReturnValue
    default NamePartJoiner afterMapping(@Nonnull Function<String, String> mapper) {
        return parts -> join(parts.stream().map(mapper).collect(Collectors.toList()));
    }
    
    /**
     * Joins the parts with the provided separator.
     *
     * @param separator Separator for the parts.
     *
     * @return Joiner that separates the parts with the given separator.
     */
    static NamePartJoiner joinWith(@Nonnull String separator) {
        return parts -> {
            StringJoiner joiner = new StringJoiner(separator);
            parts.forEach(joiner::add);
            return joiner.toString();
        };
    }
    
    private static String toTitleCase(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
