<html>
<table cellpadding="10">
<tr><td><h4>Element $name$</h4></td></tr>
$if (nodeMappings)$
    $nodeMappings : { nm |
        <tr><td><b>&lt;- $nm.inputPath$</b></td></tr>
    }$
$endif$
$if (doc)$
    $doc.lines:{ line |
        <tr><td><p>$line$</p></td></tr>
    }$
$endif$
$if (options)$
    <tr><td>
        <ul>
            $options.opts:{ opt |
                <li>$opt.key$ - $opt.content$</li>
            }$
        </ul>
    </td></tr>
$endif$
$if (node)$
    <tr><td>
        <h1>Dropped here:</h1>
        <h3>$node.path$</h3>
    </td></tr>
$endif$
</table>
</html>