<table>

<tr><td>
<h3>$path.tail$ ($stats.total$)</h3>
</td></tr>

<tr><td>
$stats.summary$
</td></tr>

$if (stats.sample)$

<tr><td>
<h4>Sample</h4>
</td></tr>
<tr><td>
<table>
$stats.sample.selection:{ value |
<tr><td>"$value$"</td></tr>
}$
</table>
</td></tr>

$endif$

</table>
