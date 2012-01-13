package eu.delving.metadata;

/**
* todo: javadoc
*
* @author Gerald de Jong <gerald@delving.eu>
*/
public class Out {
    private int indentLevel;
    private StringBuilder stringBuilder = new StringBuilder();

    public void line(String line) {
        for (int walk = 0; walk < indentLevel; walk++) stringBuilder.append("  ");
        stringBuilder.append(line).append('\n');
    }

    public void line(String pattern, Object... params) {
        line(String.format(pattern, params));
    }

    public void before() {
        indentLevel++;
    }

    public void after() {
        indentLevel--;
    }

    @Override
    public String toString() {
        return stringBuilder.toString();
    }
}
