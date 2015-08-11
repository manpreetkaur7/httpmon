_If you want to make this software better for yourself and others, contact me with bugs, problems, suggestions, and feedback: jeffrey.blattman@gmail.com._

To start monitoring your HTTP servers, you must create one or more _monitors_. A monitor has 4 basic components:

  * Name
  * Request
  * Conditions (one or more)
  * Actions (zero or more)

A monitor's _request_ describes how to contact the server. _Conditions_ define the conditions that must hold in order for the monitor to be considered valid. _Actions_ are actions that are taken when the monitor is not valid. The purpose of an action is to alert you in some manner when the server is determined to be invalid.

When you first start httpmon, you will see a message indicating that you have not yet defined any monitors. Choose **Menu>New Monitor** to begin creating a monitor. Fill out the _edit monitor_ form by giving your monitor a name, defining a request, defining one or more conditions, and optionally defining one or more actions. Editing the monitor's request and adding or editing conditions and actions will bring you to a new form. When finished editing, press back to return to the edit monitors form. To add conditions or actions, select the type of condition or action, and click the "add" button. Depending on the condition or action type, you may need to fill out another form to set parameters for the condition / action. When done filling out the condition / action form, click **Menu>Save** or **back**. When you are done creating your monitor, press **back**, or **Menu>Save** again.

To start your monitor, long-press it the monitor list, and select **Start**. Alternatively, use **Menu>Start All**.

For a lists of objects in httpmon (monitors, conditions, and actions), edit the object by pressing on the list item. Long press on the list item to bring up additional options such as remove and start / stop for monitors.

Monitors are automatically stopped when edited. You must re-start manually after your edit is complete.

# Preferences #

  * **Start at Boot?** If enabled, httpmon will restart all running monitors when your phone boots.
  * **Connection / Read Timeout** The maximum time to open a connection / read response from a monitor.
  * **User Agent** Set the user agent header to use when contacting monitors.
  * **Background Notification?** If enabled, httpmon will add an icon to the notification bar whenever monitors are running in the background.

# View Log #

**Menu > View** allows you to view a detailed log of httpmon's activity. This can be useful in determining why a monitor failed.