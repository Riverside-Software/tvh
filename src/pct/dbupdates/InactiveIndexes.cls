routine-level on error undo, throw.

/**
 * Generates text file (one entry per line) for each inactive index loaded during a schema load.
 * Used later in the DbUpdates process to rebuild only those indexes
 */
class dbupdates.InactiveIndexes inherits rssw.pct.AbstractLoadLogger:

  method public override void addIndex(pcAction as char, pcTable as char, h as handle, piArea as int):
    this-object:addIndex(pcAction, pcTable, h, piArea, false).
  end method.

  method public override void addIndex(pcAction as char, pcTable as char, h as handle, piArea as int, piPrimary as logical):
    if (not h:buffer-field('_active'):buffer-value) then do:
        output to value("NewIndexes-" + ldbname(1)) append.
        put unformatted pcTable + ";" + h:buffer-field('_index-name'):buffer-value skip.
        output close.
    end.
  end method.

end class.
