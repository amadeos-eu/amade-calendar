package eu.amadeos.apps.calendar.domain;

/** Which occurrences a change or deletion of a repeating appointment applies to, as dtcm asked. */
public enum EditScope {
    /** Only the occurrence shown: it becomes an exception of the series. */
    THIS_ONE,
    /** This occurrence and all later ones: the series ends before it and a new one starts here. */
    FORWARD,
    /** The whole series. */
    ALL
}
