<html>
<center>

$if (entry)$

<h1>$entry.spec$</h1>
<p>Organization: $entry.organization$</p>
<table width=90%>
<tr><td>

$if (entry.dataSet)$

<p>Local:</p>

<dl>

$entry.dataSet.schemaVersions:{ sv |
  <dt>Schema $sv.prefix$</dt>
    <dd>Version $sv.version$</dd>
}$

</dl>

$endif$

$if (entry.dataSetEntry)$

<p>Online:</p>
<dl>

  <dt>Created by:</dt>
    <dd>$entry.dataSetEntry.createdBy.fullname$ &lt;$entry.dataSetEntry.createdBy.email$&gt;</dd>

  <dt>Record Count:</dt>
    <dd>$entry.dataSetEntry.recordCount$</dd>

$if (entry.dataSetEntry.lockedBy)$
  <dt>Owned by:</dt>
    <dd>$entry.dataSetEntry.lockedBy.fullname$ &lt;$entry.dataSetEntry.lockedBy.email$&gt;</dd>
$endif$

</dl>

$endif$

</td></tr>
</table>

$else$

<h1>Select a data set</h1>

$endif$

</center>
</font>
</html>


<!--


-->