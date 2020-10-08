OUTPUT TO VALUE(SESSION:PARAMETER).

FOR EACH dictdb._area WHERE dictdb._area._area-num >= 7 BY _area._area-name:
    PUT UNFORMATTED SUBSTITUTE('A &1.&2', LDBNAME(1), dictdb._area._area-name) SKIP.
END.

FOR EACH dictdb._sequence BY dictdb._sequence._seq-name:
    PUT UNFORMATTED SUBSTITUTE('S &1.&2', LDBNAME(1), dictdb._sequence._seq-name) SKIP.
END.

FOR EACH dictdb._file WHERE dictdb._file._tbl-type = 'T' BY dictdb._file._file-name : 
    PUT UNFORMATTED SUBSTITUTE('T &1.&2 &3', LDBNAME(1), dictdb._file._file-name, dictdb._file._CRC) SKIP.
    FOR EACH dictdb._Index WHERE dictdb._Index._File-recid EQ RECID(dictdb._file) BY _Index._index-name:
        PUT UNFORMATTED SUBSTITUTE('I &1.&2.&3 &4', LDBNAME(1), dictdb._file._file-name, dictdb._index._index-name, dictdb._index._idx-CRC) SKIP.
    END.
END.

OUTPUT CLOSE.
RETURN '0'.
