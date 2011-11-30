<html>
<table cellpadding="10">
<tr><td><h1>$name$</h1></td></tr>
$if (doc)$
    $doc:{ line |
        <tr><td><p>$line$</p></td></tr>
    }$
$endif$
</table>
</html>