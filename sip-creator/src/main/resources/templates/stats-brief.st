<html>
<table>

<tr><td>
<h1>$stats.path.tail$ ($stats.total$)</h1>
</td></tr>

<tr><td>
$stats.summary$
</td></tr>

$if (stats.histogram)$
<tr><td>
<h3>Histogram</h3>
</td></tr>
<tr><td>
<table>
$stats.histogram.firstValues:{ counter |
<tr><td>$counter.percentage$</td><td>$counter.value$ ($counter.count$)</td></tr>
}$
</table>
</td></tr>

$else$
$if (stats.randomSample)$

<tr><td>
<h3>Sample</h3>
</td></tr>
<tr><td>
<table>
$stats.randomSample.firstValues:{ value |
<tr><td>$value$</td></tr>
}$
</table>
</td></tr>

$endif$
$endif$

</table>
</html>