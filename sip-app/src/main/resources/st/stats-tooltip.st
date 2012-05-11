<html>
<body>
<table>

<tr><td>
<h3>$path.tail$ ($stats.total$)</h3>
</td></tr>

<tr><td>
$stats.summary$
</td></tr>
$if (stats.values)$

    <tr><td>
        <h4>Histogram</h4>
    </td></tr>
    <tr><td>
    <table>
        $stats.values.toolTip:{ counter |
        <tr><td>$counter$</td></tr>
        }$
    </table>
$endif$
$if (stats.sample)$
    <tr><td>
        <h4>Sample</h4>
    </td></tr>
    <tr><td>
    <table>
        $stats.sample.toolTip:{ value |
        <tr><td>"$value$"</td></tr>
        }$
    </table>
    </td></tr>
$endif$
</table>
</body>
</html>