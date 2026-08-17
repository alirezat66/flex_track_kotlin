package dev.taghizadeh.flextrack.routing

@JvmInline
public value class EventCategory(public val name: String) {
    init {
        require(name.isNotEmpty()) { "category name cannot be empty" }
    }

    public companion object {
        public val Business: EventCategory = EventCategory("business")
        public val User: EventCategory = EventCategory("user")
        public val Technical: EventCategory = EventCategory("technical")
        public val Sensitive: EventCategory = EventCategory("sensitive")
        public val Marketing: EventCategory = EventCategory("marketing")
        public val System: EventCategory = EventCategory("system")
        public val Security: EventCategory = EventCategory("security")
    }
}
