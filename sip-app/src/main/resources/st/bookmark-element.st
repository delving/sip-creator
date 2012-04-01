<html>
<table cellpadding="10">
<tr><td><h1>Element $name$</h1></td></tr>
$if (doc)$
    $doc:{ line |
        <tr><td><p>$line$</p></td></tr>
    }$
$endif$
$if (discriminators)$
    <tr><td>
        <ul>
            $options:{ discriminator |
                <li>$discriminator.key$ - $discriminator.content$</li>
            }$
        </ul>
    </td></tr>
$endif$
</table>
</html>