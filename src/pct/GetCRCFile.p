/* Checks for valid parameters */
IF (SESSION:PARAMETER EQ ?) THEN
    RETURN '20'.

/* Filename as parameter */
RUN write_schema.p.

RETURN '0'.
