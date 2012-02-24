<html>
<table cellpadding="10">
<tr><td><h4>Attribute $name$</h4></td></tr>
$if (doc)$
    $doc:{ line |
        <tr><td><p>$line$</p></td></tr>
    }$
$endif$
</table>
</html>