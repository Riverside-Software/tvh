/**
 * Callback class for schema load. Only with dynamic load as the DbUpdates may not be available yet.
 */
class dbupdates.SchemaLoadLogger inherits rssw.pct.AbstractLoadCallback:
  define property initialUpdate as logical initial false get. set.

  method public override void beforeFile(file as character):
    define variable h as handle no-undo.
    define variable qry as handle no-undo.
    define variable dbNames as character no-undo.
    define variable zz as integer no-undo.

    do zz = 1 to num-dbs:
      assign dbNames = dbNames + (if dbNames eq '' then '' else ',') + ldbname(zz).
    end.

    do on error undo, retry:
      /* Specific case when the DbUpdates is not yet available (very first update) */
      if retry then do:
        message "No DbUpdates table, message (7334) is expected".
        initialUpdate = true.
        return.
      end.
      create buffer h for table 'DbUpdates'.
    end.

    do transaction:
      create query qry.
      qry:set-buffers(h).
      qry:query-prepare('for each dbupdates where dbversionupdate = ' + dynamic-function('getParameter', 'dbupdatenum') + ' and dbversionstep = ' + quoter(dynamic-function('getParameter', 'dbupdatestep'))).
      qry:query-open().
      if (not qry:get-first(exclusive-lock)) then do:
        h:buffer-create().
        h:buffer-field('DbVersionUpdate'):buffer-value = int(dynamic-function('getParameter', 'dbUpdateNum')).
        h:buffer-field('DbVersionStep'):buffer-value = dynamic-function('getParameter', 'dbUpdateStep').
      end.
      h:buffer-field('UpdateType'):buffer-value = 'Schema'.
      h:buffer-field('StartDate'):buffer-value = now.
      h:buffer-field('EndDate'):buffer-value = ?.
      // h:buffer-field('HotFix'):buffer-value = false.
      qry:query-close().
    end.

  end method.

  method public override void afterFile(file as character, logger as rssw.pct.LoadLogger):
    define variable h as handle no-undo.
    define variable qry as handle no-undo.
    define variable dbNames as character no-undo.
    define variable zz as integer no-undo.

    do zz = 1 to num-dbs:
      assign dbNames = dbNames + (if dbNames eq '' then '' else ',') + ldbname(zz).
    end.

    create buffer h for table 'DbUpdates'.

    if initialUpdate then do transaction:
      h:buffer-create().
      h:buffer-field('DbVersionUpdate'):buffer-value = int(dynamic-function('getParameter', 'dbUpdateNum')).
      h:buffer-field('DbVersionStep'):buffer-value = dynamic-function('getParameter', 'dbUpdateStep').
      h:buffer-field('UpdateType'):buffer-value = 'Schema'.
      h:buffer-field('StartDate'):buffer-value = now.
      h:buffer-field('EndDate'):buffer-value = now.
      h:buffer-field('LogOutput'):buffer-value = logger:getErrors().
      h:buffer-field('ReturnCode'):buffer-value = 0.
      // h:buffer-field('HotFix'):buffer-value = false.
    end.
    else do transaction:
      create query qry.
      qry:set-buffers(h).
      qry:query-prepare('for each dbupdates where dbversionupdate = ' + dynamic-function('getParameter', 'dbupdatenum') + ' and dbversionstep = ' + quoter(dynamic-function('getParameter', 'dbupdatestep'))).
      qry:query-open().
      qry:get-first(exclusive-lock).
      h:buffer-field('EndDate'):buffer-value = now.
      h:buffer-field('LogOutput'):buffer-value = logger:getErrors().
      h:buffer-field('ReturnCode'):buffer-value = logger:numErrors.
      // h:buffer-field('HotFix'):buffer-value = false.
      qry:query-close().
    end.

  end method.

end class.


