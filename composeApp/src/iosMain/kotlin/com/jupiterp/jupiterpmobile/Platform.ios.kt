@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.jupiterp.jupiterpmobile

import com.jupiterp.jupiterpmobile.domain.model.ClassMeeting
import com.jupiterp.jupiterpmobile.domain.model.DayOfWeek
import com.jupiterp.jupiterpmobile.domain.model.ScheduleSelection
import platform.EventKit.EKEntityType
import platform.EventKit.EKEvent
import platform.EventKit.EKEventStore
import platform.EventKit.EKRecurrenceDayOfWeek
import platform.EventKit.EKRecurrenceEnd
import platform.EventKit.EKRecurrenceFrequency
import platform.EventKit.EKRecurrenceRule
import platform.EventKit.EKSpan
import platform.EventKit.EKWeekdayFriday
import platform.EventKit.EKWeekdayMonday
import platform.EventKit.EKWeekdaySaturday
import platform.EventKit.EKWeekdaySunday
import platform.EventKit.EKWeekdayThursday
import platform.EventKit.EKWeekdayTuesday
import platform.EventKit.EKWeekdayWednesday
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarIdentifierGregorian
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSOperationQueue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.popoverPresentationController
import kotlin.math.roundToInt

class IOSPlatform: Platform {
    override val name: String = UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

actual fun currentDateInt(): Int {
    val formatter = NSDateFormatter().apply { dateFormat = "yyyyMMdd" }
    return formatter.stringFromDate(NSDate()).toInt()
}

actual fun addToCalendar(selections: List<ScheduleSelection>, onResult: (Boolean) -> Unit) {
    val semester = activeSemester() ?: run { onResult(false); return }
    val store = EKEventStore()
    store.requestAccessToEntityType(EKEntityType.EKEntityTypeEvent) { granted, _ ->
        NSOperationQueue.mainQueue.addOperationWithBlock {
            if (!granted) {
                onResult(false)
                return@addOperationWithBlock
            }
            val defaultCalendar = store.defaultCalendarForNewEvents
            if (defaultCalendar == null) {
                onResult(false)
                return@addOperationWithBlock
            }

            val endDate = parseIcsEndDate(semester.endIcs)
            var success = true

            for (selection in selections) {
                val courseCode = selection.course.courseCode
                val sectionCode = selection.section.sectionCode
                val courseName = selection.course.name

                for (meeting in selection.section.meetings) {
                    val days: List<DayOfWeek>
                    val startTime: Float
                    val endTime: Float
                    val location: String
                    when (meeting) {
                        is ClassMeeting.InPerson -> {
                            days = meeting.classtime.daysList
                            startTime = meeting.classtime.start
                            endTime = meeting.classtime.end
                            location = meeting.location.display
                        }
                        is ClassMeeting.OnlineSync -> {
                            days = meeting.classtime.daysList
                            startTime = meeting.classtime.start
                            endTime = meeting.classtime.end
                            location = "Online Sync"
                        }
                        else -> continue
                    }
                    if (days.isEmpty()) continue

                    // First meeting on or after the first day of classes,
                    // which may be midweek (Spring 2027 starts on a Wednesday)
                    val firstDate = days.minOf { icsDateForDay(it, semester.firstClassDayInt) }.toInt()
                    val ekDays = days.map { EKRecurrenceDayOfWeek.dayOfWeek(it.toEKWeekday()) }
                    val rule = EKRecurrenceRule(
                        recurrenceWithFrequency = EKRecurrenceFrequency.EKRecurrenceFrequencyWeekly,
                        interval = 1,
                        daysOfTheWeek = ekDays,
                        daysOfTheMonth = null,
                        monthsOfTheYear = null,
                        weeksOfTheYear = null,
                        daysOfTheYear = null,
                        setPositions = null,
                        end = EKRecurrenceEnd.recurrenceEndWithEndDate(endDate)
                    )

                    val event = EKEvent.eventWithEventStore(store)
                    event.title = "$courseCode ($sectionCode) - $location"
                    event.notes = courseName
                    event.location = location
                    event.calendar = defaultCalendar
                    event.startDate = makeEventDate(firstDate, startTime)
                    event.endDate = makeEventDate(firstDate, endTime)
                    event.addRecurrenceRule(rule)

                    val saved = runCatching {
                        store.saveEvent(event, span = EKSpan.EKSpanFutureEvents, commit = false, error = null)
                    }.getOrDefault(false)
                    if (!saved) success = false
                }
            }

            if (success) store.commit(null)
            onResult(success)
        }
    }
}

private fun makeEventDate(dateInt: Int, time: Float): NSDate {
    val components = NSDateComponents()
    components.year = (dateInt / 10000).toLong()
    components.month = ((dateInt / 100) % 100).toLong()
    components.day = (dateInt % 100).toLong()
    // Round to whole minutes to absorb float precision error in times like X:20
    val totalMinutes = (time * 60).roundToInt()
    components.hour = (totalMinutes / 60).toLong()
    components.minute = (totalMinutes % 60).toLong()
    components.second = 0L
    return NSCalendar(NSCalendarIdentifierGregorian).dateFromComponents(components)!!
}

private fun parseIcsEndDate(endIcs: String): NSDate {
    val formatter = NSDateFormatter().apply { dateFormat = "yyyyMMdd" }
    return formatter.dateFromString(endIcs.take(8)) ?: NSDate()
}

private fun DayOfWeek.toEKWeekday() = when (this) {
    DayOfWeek.MONDAY -> EKWeekdayMonday
    DayOfWeek.TUESDAY -> EKWeekdayTuesday
    DayOfWeek.WEDNESDAY -> EKWeekdayWednesday
    DayOfWeek.THURSDAY -> EKWeekdayThursday
    DayOfWeek.FRIDAY -> EKWeekdayFriday
    DayOfWeek.SATURDAY -> EKWeekdaySaturday
    DayOfWeek.SUNDAY -> EKWeekdaySunday
    else -> EKWeekdayMonday
}

actual fun shareText(text: String, subject: String?): Boolean {
    @Suppress("DEPRECATION")
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return false
    // Present over whatever is already showing (a sheet, a dialog)
    var top = root
    while (true) {
        top = top.presentedViewController ?: break
    }
    val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    // iPad shows the sheet as a popover, which needs an anchor or it crashes
    controller.popoverPresentationController?.let { popover ->
        val view = top.view
        popover.sourceView = view
        view.bounds.useContents {
            popover.sourceRect = CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0)
        }
    }
    top.presentViewController(controller, animated = true, completion = null)
    return true
}
