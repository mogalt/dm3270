package com.bytezone.dm3270.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.prefs.Preferences;

import com.bytezone.dm3270.application.Console.Function;
import com.bytezone.dm3270.display.Screen;
import com.bytezone.dm3270.plugins.PluginsStage;
import com.bytezone.dm3270.utilities.WindowSaver;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;

public class Terminal extends Application
{
  private static final int COMBO_BOX_WIDTH = 150;
  private static final int EDIT_BUTTON_WIDTH = 50;
  private static final String EDIT_BUTTON_FONT_SIZE = "-fx-font-size: 10;";

  private Preferences prefs;
  private ConsolePane consolePane;
  private WindowSaver windowSaver;
  private SiteListStage serverSitesListStage;
  private PluginsStage pluginsStage;
  private Screen screen;
  private ComboBox<String> serverComboBox;
  private Button editServersButton;

  private String requestedSite = "";

  @Override
  public void init () throws Exception
  {
    super.init ();

    prefs = Preferences.userNodeForPackage (this.getClass ());
    List<String> parms = new ArrayList<> ();

    for (String raw : getParameters ().getRaw ())
      if ("-reset".equalsIgnoreCase (raw))
        prefs.clear ();
      else if (raw.startsWith ("-site="))
        requestedSite = raw.substring (6);
      else if (!raw.startsWith ("-"))
        parms.add (raw);
      else
        System.out.printf ("Unknown argument: %s%n", raw);

    if (parms.size () > 0 && requestedSite.isEmpty ())
      requestedSite = parms.get (0);

    if (false)
    {
      String[] keys = prefs.keys ();
      Arrays.sort (keys);
      for (String key : keys)
        if (key.matches ("Server.*Name"))
          System.out.printf ("%-18s : %s%n", key, prefs.get (key, ""));
    }
  }

  @Override
  public void start (Stage primaryStage) throws Exception
  {
    serverSitesListStage = new SiteListStage (prefs, "Server", 5, true);
    pluginsStage = new PluginsStage (prefs);

    Site serverSite = null;

    if (!requestedSite.isEmpty ())        // using a command line argument
    {
      Optional<Site> optionalServerSite =
          serverSitesListStage.getSelectedSite (requestedSite);
      if (optionalServerSite.isPresent ())
        serverSite = optionalServerSite.get ();
    }

    while (serverSite == null)
    {
      // show request dialog
      Optional<String> result = build ();
      if (result.isPresent ())
      {
        String name = result.get ();
        if (name.equals (":EDIT:"))
        {
          System.out.println ("Edit....");
        }
        else if (name.equals (":CANCEL:"))
        {
          break;
        }
        else
        {
          Optional<Site> optionalServerSite = serverSitesListStage.getSelectedSite (name);
          if (optionalServerSite.isPresent ())
          {
            serverSite = optionalServerSite.get ();
            break;
          }
        }
      }
    }

    if (serverSite != null)
    {
      screen = new Screen (24, 80, prefs, Function.TERMINAL, pluginsStage, serverSite);

      consolePane = new ConsolePane (screen, serverSite, pluginsStage);
      consolePane.connect (serverSite);
      Scene scene = new Scene (consolePane);

      windowSaver = new WindowSaver (prefs, primaryStage, "Terminal");
      if (!windowSaver.restoreWindow ())
        primaryStage.centerOnScreen ();

      primaryStage.setScene (scene);
      primaryStage.setTitle ("dm3270");

      scene.setOnKeyPressed (new ConsoleKeyPress (consolePane, screen));
      scene.setOnKeyTyped (new ConsoleKeyEvent (screen));

      primaryStage.sizeToScene ();
      primaryStage.show ();
    }
  }

  private Optional<String> build ()
  {
    String serverSelected = prefs.get ("ServerName", "");
    //    Optional<Site> site = serverSitesListStage.getSelectedSite (serverSelected);

    ComboBox<String> siteList = serverSitesListStage.getComboBox ();
    //    serverComboBox.setPrefWidth (COMBO_BOX_WIDTH);
    //    serverComboBox.setVisibleRowCount (5);
    siteList.getSelectionModel ().select (serverSelected);
    Label label = new Label ("Select a server ");

    editServersButton = serverSitesListStage.getEditButton ();
    editServersButton.setStyle (EDIT_BUTTON_FONT_SIZE);
    editServersButton.setMinWidth (EDIT_BUTTON_WIDTH);

    Dialog<String> dialog = new Dialog<> ();

    GridPane grid = new GridPane ();
    grid.add (label, 1, 1);
    grid.add (siteList, 2, 1);
    grid.add (editServersButton, 3, 1);
    grid.setHgap (10);
    grid.setVgap (10);
    dialog.getDialogPane ().setContent (grid);

    ButtonType btnTypeOK = new ButtonType ("OK", ButtonData.OK_DONE);
    ButtonType btnTypeCancel = new ButtonType ("Cancel", ButtonData.CANCEL_CLOSE);
    //    ButtonType btnTypeEdit = new ButtonType ("Edit", ButtonData.OTHER);
    dialog.getDialogPane ().getButtonTypes ().addAll (btnTypeOK, btnTypeCancel);
    dialog.setResultConverter (btnType ->
    {
      if (btnType == btnTypeOK)
        return siteList.getSelectionModel ().getSelectedItem ();
      if (btnType == btnTypeCancel)
        return ":CANCEL:";
      return ":UNKNOWN:";
    });

    return dialog.showAndWait ();
  }

  @Override
  public void stop ()
  {
    if (consolePane != null)
      consolePane.disconnect ();

    if (windowSaver != null)
      windowSaver.saveWindow ();

    if (screen != null)
      screen.closeAssistantStage ();
  }

  public static void main (String[] args)
  {
    launch (args);
  }
}