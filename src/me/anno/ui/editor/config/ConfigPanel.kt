package me.anno.ui.editor.config

import me.anno.gpu.GFX.windowStack
import me.anno.io.utils.StringMap
import me.anno.ui.base.buttons.TextButton
import me.anno.ui.base.Panel
import me.anno.ui.base.TextPanel
import me.anno.ui.base.groups.PanelListX
import me.anno.ui.base.groups.PanelListY
import me.anno.ui.base.scrolling.ScrollPanelXY
import me.anno.ui.custom.CustomListX
import me.anno.ui.input.TextInput
import me.anno.ui.style.Style
import kotlin.math.max

// todo allow fields to be added
class ConfigPanel(val config: StringMap, val isStyle: Boolean, style: Style) : PanelListY(style) {

    val deep = style.getChild("deep")
    val searchBar = PanelListX(deep)

    val mainBox = CustomListX(style)
    val topicTree = PanelListY(style)
    val contentListUI = PanelListY(style)
    val contentList = ArrayList<Pair<String, Panel>>()

    val searchInput = TextInput("Search", deep)

    fun create() {
        createTopics()
        if (topicTree.children.isNotEmpty()) {
            val tp = topicTree.children.first() as TopicPanel
            createContent(tp.topic)
        }
        fun add(panel: Panel, weight: Float) {
            mainBox.add(ScrollPanelXY(panel.withPadding(5, 5, 5, 5), style), weight)
        }
        add(topicTree, 1f)
        add(contentListUI, 3f)
        searchBar += TextButton("Close", false, deep)
            .setSimpleClickListener { windowStack.pop().destroy() }
        if (isStyle) {
            searchBar += TextButton("Apply", false, deep).setSimpleClickListener {
                createTopics()
                lastTopic = "-"
                applySearch(searchInput.text)
            }
        }
        searchBar += searchInput.apply {
            setChangeListener { query -> applySearch(query) }
            weight = 1f
        }
        this += mainBox
        this += searchBar
    }

    fun applySearch(query: String) {

        if (query.isBlank()) {

            createContent(lastNotEmptyTopic)

        } else {

            val queryTerms = query
                .replace('\t', ',')
                .replace(' ', ',')
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .map { it.toLowerCase() }

            if (lastTopic.isNotEmpty()) createContent("")
            contentList.forEach { (name, ui) ->
                if (queryTerms.all { it in name }) {
                    ui.show()
                } else ui.hide()
            }

        }

    }

    var lastTopic = ""
    var lastNotEmptyTopic = ""
    fun createTopics() {
        topicTree.clear()
        // concat stuff, that has only one entry?
        // descriptions???...
        val topics = config.keys.map {
            val lastIndex = it.lastIndexOf('.')
            it.substring(0, max(0, lastIndex))
        }.toHashSet()
        for (i in 0 until 5) {
            topics.addAll(
                topics.map {
                    val lastIndex = it.lastIndexOf('.')
                    it.substring(0, max(0, lastIndex))
                }
            )
        }
        for (topic in topics.sortedBy { it.toLowerCase() }) {
            if (topic.isNotEmpty()) {
                val lastIndex = topic.lastIndexOf('.')
                val topicName = topic.substring(lastIndex + 1)
                val panel = TopicPanel(topic, topicName, this, style)
                if (panel.topicDepth > 0) panel.hide()
                topicTree += panel
                /*panel.setSimpleClickListener {// show that there is more? change the name?
                    val start = "$topic."
                    val special = Input.isShiftDown || Input.isControlDown
                    val visible = if(special) Visibility.GONE else Visibility.VISIBLE
                    if(!special) createContent(topic, topicName)
                    topicTree.children
                        .filterIsInstance<TopicPanel>()
                        .forEach {
                            if(it.topic.startsWith(start)){
                                it.visibility = visible
                            }
                        }
                }*/
            }
        }
        invalidateLayout()
    }

    fun createContent(topic: String) {

        lastTopic = topic
        if (topic.isNotEmpty()) lastNotEmptyTopic = topic

        contentListUI.clear()
        contentList.clear()

        val pattern = if (topic.isEmpty()) "" else "$topic."
        val entries = config.entries
            .filter { it.value !is StringMap }
            .filter { it.key.startsWith(pattern) }
            .map {
                val fullName = it.key
                val relevantName = fullName.substring(pattern.length)
                val depth = relevantName.count { char -> char == '.' }
                val li = relevantName.lastIndexOf('.') + 1
                val key = relevantName.substring(0, max(0, li - 1))
                val shortName = relevantName.substring(li)
                ContentCreator(fullName, relevantName, depth, key, shortName, config)
            }
            .sortedBy { it.relevantName }

        val largeHeaderText = style.getChild("header")
        val smallHeaderStyle = style.getChild("header.small")

        val topList = entries.filter { it.depth == 0 }

        // add header
        val largeHeader2 = TextPanel(topic, largeHeaderText).apply { font = font.withItalic(true) }
        contentListUI += largeHeader2
        val subChain = StringBuilder(topList.size * 2)
        for (entry in topList) {
            val subList = PanelListY(style)
            subList.setTooltip(entry.fullName)
            entry.createPanels(subList)
            val searchKey = entry.fullName.toLowerCase()
            contentList += searchKey to subList
            contentListUI += subList
            subChain.append(searchKey)
            subChain.append(' ')
        }
        contentList += subChain.toString() to largeHeader2


        // add sub headers for all topics...
        val groups = entries
            .filter { it.depth > 0 }
            .groupBy { it.groupName }

        for (group in groups.entries.sortedBy { it.key }) {
            val groupName = group.key
            val smallHeader = TextPanel(groupName, smallHeaderStyle)
            contentListUI += smallHeader
            val subChain2 = StringBuilder(group.value.size * 2)
            for (entry in group.value) {
                val subList = PanelListY(style)
                subList.setTooltip(entry.fullName)
                entry.createPanels(subList)
                val searchKey = entry.fullName.toLowerCase()
                contentList += searchKey to subList
                contentListUI += subList
                subChain2.append(searchKey)
                subChain2.append(' ')
            }
            contentList += subChain2.toString() to smallHeader
        }

        invalidateLayout()

    }

}