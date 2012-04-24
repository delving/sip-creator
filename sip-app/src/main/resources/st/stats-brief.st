<table>

<tr><td>
<h3>$path.tail$ ($stats.total$)</h3>
</td></tr>

<tr><td>
$stats.summary$
</td></tr>

$if (stats.histogram)$
<tr><td>
<h4>Histogram</h4>
</td></tr>
<tr><td>
<table>
$stats.histogram.firstValues:{ counter |
<tr><td>$counter.percentage$</td><td>$counter.value$ ($counter.count$)</td></tr>
}$
</table>
</td></tr>

$endif$
$if (stats.sample)$

<tr><td>
<h4>Sample</h4>
</td></tr>
<tr><td>
<table>
$stats.sample.firstValues:{ value |
<tr><td>$value$</td></tr>
}$
</table>
</td></tr>

$endif$

</table>
