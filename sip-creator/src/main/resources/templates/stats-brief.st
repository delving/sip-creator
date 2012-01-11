<html>
<table>
<tr><td>
<h1>$stats.path.tail$ ($stats.total$)</h1>
</td></tr>
$if (stats.histogram)$
<tr><td>
<table>
$stats.histogram.firstValues:{ counter |
<tr><td>$counter.percentage$</td><td>$counter.value$ ($counter.count$)</td></tr>
}$
$endif$
</table>
</td></tr>
</table>
</html>