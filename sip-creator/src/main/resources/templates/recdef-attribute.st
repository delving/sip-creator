<html>
<table cellpadding="10">
<tr><td><h1>Attribute $name$</h1></td></tr>
$if (doc)$
    $doc.lines:{ line |
        <tr><td><p>$line$</p></td></tr>
    }$
$endif$
$if (node)$
    <tr><td>
        <h1>Dropped here:</h1>
        <h3>$node.path$</h3>
    </td></tr>
$endif$
</table>
</html>