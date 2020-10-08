define output parameter v as integer no-undo INITIAL '0'.
define output parameter s as integer no-undo INITIAL '0'.

DEFINE VARIABLE hFile   AS HANDLE     NO-UNDO.
DEFINE VARIABLE hQuery  AS HANDLE     NO-UNDO.
DEFINE VARIABLE hFld1   AS HANDLE     NO-UNDO.
DEFINE VARIABLE hFld2   AS HANDLE     NO-UNDO.

CREATE BUFFER hFile FOR TABLE 'DbUpdates' NO-ERROR.
IF VALID-HANDLE(hFile) THEN DO:
    CREATE QUERY hQuery.
    hQuery:SET-BUFFERS(hFile).
    hFld1 = hFile:BUFFER-FIELD('DbVersionUpdate':U).
    hFld2 = hFile:BUFFER-FIELD('DbVersionStep':U).
    hQuery:QUERY-PREPARE('FOR EACH DbUpdates WHERE ReturnCode EQ 0 AND EndDate NE ? BY DbVersionUpdate DESCENDING BY DbVersionStep DESCENDING').
    hQuery:QUERY-OPEN().
    REPEAT:
        IF NOT hQuery:GET-NEXT() THEN LEAVE.
        ASSIGN  v = hFld1:BUFFER-VALUE
                s = hFld2:BUFFER-VALUE.
        LEAVE.
    END.
    hQuery:QUERY-CLOSE().
    DELETE OBJECT hFld1.
    DELETE OBJECT hFld2.
    DELETE OBJECT hQuery.
    DELETE OBJECT hFile.
END.

RETURN '0'.
