package org.genepattern.gpge.ui.maindisplay;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.*;
import java.io.*;
import java.util.*;
import java.net.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.*;
import javax.swing.tree.*;
import org.genepattern.gpge.*;
import org.genepattern.gpge.io.*;
import org.genepattern.gpge.ui.browser.*;
import org.genepattern.gpge.ui.graphics.draggable.*;
import org.genepattern.gpge.ui.maindisplay.*;
import org.genepattern.gpge.ui.preferences.*;
import org.genepattern.gpge.ui.tasks.*;
import org.genepattern.gpge.ui.project.*;
import org.genepattern.gpge.ui.treetable.*;
import org.genepattern.gpge.util.BuildProperties;
import org.genepattern.modules.ui.graphics.*;
import org.genepattern.util.*;
import org.genepattern.webservice.*;
/**
 *  Description of the Class
 *
 * @author    Joshua Gould
 */
public class MainFrame extends JFrame {
   public static boolean RUNNING_ON_MAC = System.getProperty("mrj.version") != null && javax.swing.UIManager.getSystemLookAndFeelClassName().equals(javax.swing.UIManager.getLookAndFeel().getClass().getName());
   AnalysisServicePanel analysisTasksPanel;

   JLabel messageLabel = new JLabel();
   AnalysisServiceManager analysisServiceManager;
   final static Color AUTHORITY_MINE_COLOR = java.awt.Color.decode("0xFF00FF");
   final static Color AUTHORITY_FOREIGN_COLOR = java.awt.Color.decode("0x0000FF");

   AnalysisMenu analysisMenu;
   AnalysisMenu visualizerMenu;

   JPopupMenu jobPopupMenu = new JPopupMenu();
   JPopupMenu projectDirPopupMenu;
   JPopupMenu serverFilePopupMenu = new JPopupMenu();
   SortableTreeTable jobResultsTree;
   JobModel jobModel;
   SortableTreeTable projectDirTree;
   ProjectDirModel projectDirModel;
   DefaultMutableTreeNode selectedJobNode = null;
   DefaultMutableTreeNode selectedProjectDirNode = null;
   
   JFileChooser saveAsFileChooser = new JFileChooser();
   FileMenu fileMenu;
   final static int MENU_SHORTCUT_KEY_MASK = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
   FileInfoComponent fileSummaryComponent = new FileInfoComponent();
   
         
   private static ParameterInfo copyParameterInfo(ParameterInfo toClone) {
		ParameterInfo pi = new ParameterInfo(toClone.getName(), toClone.getValue(), toClone.getDescription());
		HashMap attrs = toClone.getAttributes();
		if(attrs != null) {
			attrs  = (HashMap) attrs.clone();
		} else {
			attrs = new HashMap(1);
		}
		pi.setAttributes(attrs);
		return pi;
	}
   
   
   public void showSaveDialog(final JobModel.ServerFileNode node) { 
		final File initiallySelectedFile = new File(node.toString());
		saveAsFileChooser.setSelectedFile(initiallySelectedFile);
		
		if(saveAsFileChooser.showSaveDialog(GenePattern.getDialogParent())==JFileChooser.APPROVE_OPTION) {
			final File outputFile = saveAsFileChooser.getSelectedFile();
			if(outputFile.exists()) {
				String message = "An item named " + outputFile.getName() + " already exists in this location. Do you want to replace it with the one that you are saving?";
				if(JOptionPane.showOptionDialog(GenePattern.getDialogParent(), message, null, JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE,null,new Object[] {"Replace", "Cancel"}, "Cancel")!=JOptionPane.YES_OPTION) { 					return;
				}
				
			}
			 
			new Thread() { 
				public void run() {
					try {
						node.download(outputFile);
					} catch(Exception e) {
						GenePattern.showError(GenePattern.getDialogParent(), "Error saving file", e);
					}
				}
			}.start();
		}
	 }
   
   /**
	*Loads a task with the parameters that were used in the specified job into the AnalysisTaskPanel 
	@param job the job
	*/
	public void reload(AnalysisJob job) {
		String taskName = job.getTaskName();
		String lsid = job.getLSID();
		String key = lsid!=null?lsid:taskName;
		//this won't reload old jobs b/c they have no lsid
		AnalysisService service = analysisServiceManager.getAnalysisService(key);
		
		if(service==null) {
			if(lsid!=null) {
				service = analysisServiceManager.getAnalysisService(lsid); // see if old version of task exists
			}
			if(service==null) { // get task by name
				service = analysisServiceManager.getAnalysisService(taskName);
			}
			if(service==null) {
				JOptionPane.showMessageDialog(GenePattern.getDialogParent(), taskName + " does not exist.");
				return;
			}
		}
	
		TaskInfo task = service.getTaskInfo();
		org.genepattern.webservice.JobInfo savedJobInfo = job.getJobInfo();
		ParameterInfo[] savedParameters = savedJobInfo.getParameterInfoArray();
								  
		ParameterInfo[] formalParams =  task.getParameterInfoArray();
		java.util.List actualParams = new java.util.ArrayList();
		Map savedParamName2Param = new HashMap();
		for(int i = 0; savedParameters != null && i < savedParameters.length; i++) {
			if (!savedParameters[i].isOutputFile()) {
				savedParamName2Param.put(savedParameters[i].getName(), savedParameters[i]);
			}
		}
		StringBuffer errorMessage = new StringBuffer();
		if(formalParams!=null) {
			Map formalParamName2Param = new HashMap(formalParams.length);
			for(int i = 0, length = formalParams.length; i < length; i++) {
				formalParamName2Param.put(formalParams[i].getName(), formalParams[i]);
			}
			
			for(int i = 0, length = formalParams.length; i < length; i++) {
				// check to see that the saved parameters are the same as the parameters for the current installed task

				ParameterInfo formalParameterInfo = formalParams[i];
				String sOptional = (String)formalParameterInfo.getAttributes().get(GPConstants.PARAM_INFO_OPTIONAL[0]);
				boolean optional = (sOptional != null && sOptional.length() > 0);
				ParameterInfo savedParameterInfo = (ParameterInfo) savedParamName2Param.get(formalParams[i].getName());

				String sDefault = (String)formalParameterInfo.getAttributes().get(GPConstants.PARAM_INFO_DEFAULT_VALUE[0]);
			
				if(savedParameterInfo==null && !optional) { // XXX do a more stringent check
					errorMessage.append(formalParameterInfo.getName() + " seems to be a new or renamed parameter.\n");
					ParameterInfo actualParameterInfo = copyParameterInfo(formalParameterInfo);
					actualParams.add(actualParameterInfo);
					continue;
				}
				String actualValue = null; // the value to set the parameter for the job we are about to submit
			
				if (savedParameterInfo != null) { // saved parameter exists in installed task
					savedParamName2Param.remove(savedParameterInfo.getName());
					if(savedParameterInfo.isOutputFile()) {
						continue;	
					}
					if(ParameterInfo.CACHED_INPUT_MODE.equals(savedParameterInfo.getAttributes().get(ParameterInfo.MODE))) { //  input file is result of previous job on server 
						String fileNameOnServer = savedParameterInfo.getValue();
						ParameterInfo pi = new ParameterInfo(savedParameterInfo.getName(), "", savedParameterInfo.getDescription());
						HashMap attrs = new HashMap(1);
						pi.setAttributes(attrs);
						pi.getAttributes().put(GPConstants.PARAM_INFO_DEFAULT_VALUE[0], fileNameOnServer);
						pi.setAsInputFile();
						actualParams.add(pi);
						continue;
					}
					else if(savedParameterInfo.isInputFile()) { // input file is local file
						actualValue = (String) savedParameterInfo.getAttributes().get(GPConstants.PARAM_INFO_CLIENT_FILENAME[0]);
					} else {
						actualValue = savedParameterInfo.getValue();
					}
				}
				
				if (actualValue == null) { // new parameter in installed task
					if (sDefault != null && sDefault.indexOf(";") != -1) {
						actualValue = sDefault; // use default value for installed param
					} else {
						actualValue = formalParameterInfo.getValue();
					}
					
				}
				if (actualValue != null) { 
					ParameterInfo submitParam = copyParameterInfo(formalParameterInfo);
					submitParam.getAttributes().put(GPConstants.PARAM_INFO_DEFAULT_VALUE[0], actualValue);
					actualParams.add(submitParam);
				}
			}
		}

		
		if(savedParamName2Param.size() > 1) { // whatever is left is an un-recycled parameter.  Let the user know.
			errorMessage.append("Ignoring now-unused parameters ");	
		} else if(savedParamName2Param.size()==1) {
			errorMessage.append("Ignoring now-unused parameter ");
		}
		
		for (Iterator iUnused = savedParamName2Param.keySet().iterator(); iUnused.hasNext(); ) {
			 errorMessage.append(iUnused.next() + "\n");
		}

		if (errorMessage.length() > 0) {
			JOptionPane.showMessageDialog(GenePattern.getDialogParent(), errorMessage.toString());
		}
		TaskInfo taskCopy = new TaskInfo(task.getID(), task.getName(), task.getDescription(),task.getParameterInfo(),task.getTaskClassName(), task.giveTaskInfoAttributes(),task.getUserId(), task.getAccessId());
		taskCopy.setParameterInfoArray((ParameterInfo[])actualParams.toArray(new ParameterInfo[0]));
		AnalysisService serviceCopy = new AnalysisService(service.getName(), service.getURL(), taskCopy);
		analysisTasksPanel.loadTask(serviceCopy);	
		
	}
   
   public MainFrame() {
      JWindow splash = GenePattern.showSplashScreen();
      setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
      Properties prop = null;
      try {
         prop = org.genepattern.util.PropertyFactory.getInstance().getProperties("omnigene.properties");
      } catch(Exception ioe) {
         GenePattern.showErrorDialog("An error occurred while reading the omnigene properties file.");
      }
     
      
      final String server = "http://" + prop.getProperty("analysis.service.site.name");// FIXME
      
      final String username = GPpropertiesManager.getProperty("gp.user.name");
      GPpropertiesManager.setProperty("gp.user.name", username);
       
      analysisServiceManager = AnalysisServiceManager.getInstance(server, username);
         new Thread() {
            public void run() {
               try {
                  String lsidAuthority = (String) new org.genepattern.webservice.AdminProxy(server, username, false).getServiceInfo().get("lsid.authority");
                  System.setProperty("lsid.authority", lsidAuthority);
               } catch(Throwable x) {}
               refresh();
            }
         }.start();
      createMenuBar();

      analysisTasksPanel = new AnalysisServicePanel(DefaultExceptionHandler.instance(), analysisServiceManager);
      jobModel = JobModel.getInstance();
      jobModel.addJobListener(new JobListener() {
         public void jobStatusChanged(JobEvent e){}
         public void jobAdded(JobEvent e){}
         public void jobCompleted(JobEvent e){
            AnalysisJob job = e.getJob();
            int jobNumber = job.getJobInfo().getJobNumber();
            String taskName = job.getTaskName();
            String status = job.getJobInfo().getStatus();
            fileMenu.jobCompletedDialog.add(jobNumber, taskName, status);
         }
      });
      projectDirModel = ProjectDirModel.getInstance();
      projectDirTree = new SortableTreeTable(projectDirModel, false);
      
      jobModel.getJobsFromServer(server, username);
      jobResultsTree = new SortableTreeTable(jobModel);
      
      jobPopupMenu.add(
         new AbstractAction("Reload") {
            public void actionPerformed(ActionEvent e) {
               reload(((JobModel.JobNode)selectedJobNode).job);
            }
         });
      final AbstractAction deleteFilesAction =  new AbstractAction("Delete Job") {
            public void actionPerformed(ActionEvent e) {
               jobModel.delete((JobModel.JobNode)selectedJobNode);
            }
      };
      jobPopupMenu.add(deleteFilesAction);
         
    
      final JMenu saveServerFileMenu = new JMenu("Save To");
      JMenuItem saveToFileSystemMenuItem = new JMenuItem("Other...");
      saveToFileSystemMenuItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
              showSaveDialog((JobModel.ServerFileNode)selectedJobNode);
            }
      });
      projectDirModel.addProjectDirectoryListener(new ProjectDirectoryListener() {
         public void projectAdded(ProjectEvent e) {
            final File dir = e.getDirectory();
            JMenuItem menuItem = new JMenuItem(dir.getPath());
            saveServerFileMenu.add(menuItem);
            menuItem.addActionListener(new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  new Thread() { 
                     public void run() {
                        try {
                           JobModel.ServerFileNode node = (JobModel.ServerFileNode) selectedJobNode;
                           File outputFile = new File(dir, node.name);
                           node.download(outputFile);
                           projectDirModel.refresh(dir);
                        } catch(Exception e) {
                           GenePattern.showError(GenePattern.getDialogParent(), "Error saving file", e);
                        }
                     }
                  }.start();
               }
            });
         }
         
         public void projectRemoved(ProjectEvent e) {
            File dir = e.getDirectory();
            for(int i = 0; i < saveServerFileMenu.getItemCount(); i++) {
               JMenuItem m = (JMenuItem) saveServerFileMenu.getMenuComponent(i);
               if(m.getText().equals(dir.getPath())) {
                    saveServerFileMenu.remove(i);
                    break;
               }
            }
         }
      });
      saveServerFileMenu.add(saveToFileSystemMenuItem);
      serverFilePopupMenu.add(saveServerFileMenu);
         

       serverFilePopupMenu.add(
         new AbstractAction("Delete File") {
            public void actionPerformed(ActionEvent e) {
              jobModel.delete((JobModel.ServerFileNode)selectedJobNode);
            }
         });
         
      

      jobResultsTree.addMouseListener(
         new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              
               final TreePath path = jobResultsTree.getPathForLocation(e.getX(), e.getY());
               if(path == null) {
                  selectedJobNode = null;
                  return;
               }

               selectedJobNode = (DefaultMutableTreeNode) path.getLastPathComponent();
               
               
               if(selectedJobNode instanceof JobModel.ServerFileNode) {
                  JobModel.ServerFileNode node = (JobModel.ServerFileNode) selectedJobNode;
                  
                  
                  JobModel.JobNode  parent = (JobModel.JobNode) node.getParent();
                   
                  try {
                     HttpURLConnection connection = (HttpURLConnection) parent.getURL(node.name).openConnection();
                     if(connection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                        JOptionPane.showMessageDialog(GenePattern.getDialogParent(), node.name + " has been deleted from the server.");
                        jobModel.remove(node); 
                        fileSummaryComponent.select(null);
                     } else {
                        fileSummaryComponent.select(connection, node.name);
                     }
                     
                  } catch(IOException ioe) {
                     
                  } 
                  
                  
               } else {
                  try {
                     fileSummaryComponent.select(null);
                  } catch(IOException x){}
               }
               
               
               if(!e.isPopupTrigger()) {
                  return;
               }
               
               if(selectedJobNode instanceof JobModel.JobNode) {
                  JobModel.JobNode node = (JobModel.JobNode) selectedJobNode;
                  deleteFilesAction.setEnabled(node.isComplete());
                  jobPopupMenu.show(e.getComponent(), e.getX(), e.getY());
               } else if(selectedJobNode instanceof JobModel.ServerFileNode) {
                  serverFilePopupMenu.show(e.getComponent(), e.getX(), e.getY());
               }
            }
         });
      projectDirModel = ProjectDirModel.getInstance();
      
      String projectDirsString = GPpropertiesManager.getProperty("gp.project.dirs");
      if(projectDirsString!=null) {
         String[] projectDirs = projectDirsString.split(";");
         for(int i = 0; i < projectDirs.length; i++) {
            projectDirModel.add(new File(projectDirs[i]));
         }
      }
      projectDirTree = new SortableTreeTable(projectDirModel);
      projectDirPopupMenu = new JPopupMenu();
      projectDirPopupMenu.add(
         new AbstractAction("Refresh") {
            public void actionPerformed(ActionEvent e) {
              projectDirModel.refresh((ProjectDirModel.ProjectDirNode)selectedProjectDirNode);
            }
         });
         
       projectDirPopupMenu.add(
         new AbstractAction("Remove") {
            public void actionPerformed(ActionEvent e) {
              projectDirModel.remove((ProjectDirModel.ProjectDirNode)selectedProjectDirNode);
            }
         });
         
       projectDirTree.addMouseListener(
         new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
              
               final TreePath path = projectDirTree.getPathForLocation(e.getX(), e.getY());
               
               if(path == null) {
                  selectedProjectDirNode = null;
                  return;
               }
               
               
               selectedProjectDirNode = (DefaultMutableTreeNode) path.getLastPathComponent();
               
               if(selectedProjectDirNode instanceof ProjectDirModel.FileNode) {
                  ProjectDirModel.FileNode node = (ProjectDirModel.FileNode) selectedProjectDirNode;
                  ProjectDirModel.ProjectDirNode parent = (ProjectDirModel.ProjectDirNode) node.getParent();
                  FileInputStream fis = null;
                  File f = null;
                  try {
                     f = new File(parent.directory, node.file.getName());
                     fileSummaryComponent.select(f);
                  } catch(IOException ioe) {
                     if(!f.exists()) {
                        projectDirModel.refresh(parent);
                     }
                  } finally {
                     if(fis!=null) {
                        try {
                           fis.close();
                        } catch(IOException x){}
                     }
                  }
                  
                  
               } else {
                  try {
                     fileSummaryComponent.select(null);
                  } catch(IOException x){}
               }
               
               
               if(!e.isPopupTrigger()) {
                  return;
               }
               if(selectedProjectDirNode instanceof ProjectDirModel.ProjectDirNode) {
                  projectDirPopupMenu.show(e.getComponent(), e.getX(), e.getY());
               } 
            }
         });
         
      JSplitPane leftPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(projectDirTree), new JScrollPane(jobResultsTree));
      JPanel leftPanel = new JPanel(new BorderLayout());
      leftPanel.add(leftPane, BorderLayout.CENTER);
      leftPanel.add(fileSummaryComponent, BorderLayout.SOUTH);
      
      JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, analysisTasksPanel);
      getContentPane().add(splitPane, BorderLayout.CENTER);
      getContentPane().add(messageLabel, BorderLayout.SOUTH);

      java.awt.Dimension screenSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
      int width = (int) (screenSize.width * .9);
      setSize(width, (int) (screenSize.height * .9));
      setLocation((screenSize.width - getWidth()) / 2, 20);
      setTitle(BuildProperties.PROGRAM_NAME + ' ' + BuildProperties.FULL_VERSION + "  Build: " + BuildProperties.BUILD);
    //  jobResultsTree.setRootVisible(false);
      splash.hide();
      splash.dispose();
      splitPane.setDividerLocation((int)(width*0.4));
      show();
     // jobModel.add(new AnalysisJob("SAD", "ASD", new JobInfo()));
     
     
   }


   public void refresh() {
      Thread disableActions = new Thread() {
         public void run() {
            analysisMenu.setEnabled(false);
            visualizerMenu.setEnabled(false);
            fileMenu.setServerActionsEnabled(false);  
         }
      };
      if(SwingUtilities.isEventDispatchThread()) {
         disableActions.run();
      } else {
         SwingUtilities.invokeLater(disableActions);
      }
      
      new Thread() {
            public void run() {
               analysisServiceManager.refresh();
               final Collection latestTasks = analysisServiceManager.getLatestAnalysisServices();
               
               SwingUtilities.invokeLater(
                  new Thread() {
                     public void run() {
                        analysisMenu.removeAll();
                        visualizerMenu.removeAll();
                        analysisMenu.init(latestTasks);
                        visualizerMenu.init(latestTasks);
                        fileMenu.setServerActionsEnabled(true);
                         analysisMenu.setEnabled(true);
                         visualizerMenu.setEnabled(true);
                     }
                  });

            }
         }.start();

   }


   void createMenuBar() {
      JMenuBar menuBar = new JMenuBar();
      fileMenu = new FileMenu();
      menuBar.add(fileMenu);
      analysisMenu = new AnalysisMenu(false);
      analysisMenu.setEnabled(false);
      menuBar.add(analysisMenu);
      visualizerMenu = new AnalysisMenu(true);
      visualizerMenu.setEnabled(false);
      menuBar.add(visualizerMenu);
      JMenu helpMenu = new HelpMenu();

      try {
         menuBar.setHelpMenu(helpMenu);
      } catch(Throwable ex) {// setHelpMenu is not implemented on
         menuBar.add(helpMenu);// some platform/Java versions
      }
      setJMenuBar(menuBar);
   }



   class AnalysisMenu extends JMenu {
      boolean visualizer;


      public AnalysisMenu(boolean visualizer) {
         if(visualizer) {
            setText("Visualizers");
         } else {
            setText("Data Analysis");
         }
         this.visualizer = visualizer;
      }


      public void init(Collection tasks) {
         ActionListener listener =
            new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  AnalysisMenuItem mi = (AnalysisMenuItem) e.getSource();
                  analysisTasksPanel.loadTask(mi.svc);
               }
            };
         Map categories2Tasks = new TreeMap(String.CASE_INSENSITIVE_ORDER);
         for(Iterator it = tasks.iterator(); it.hasNext(); ) {
            AnalysisService svc = (AnalysisService) it.next();
            String category = (String) svc.getTaskInfo().getTaskInfoAttributes().get(GPConstants.TASK_TYPE);

            if(!visualizer && (category.equals(GPConstants.TASK_TYPE_VISUALIZER) || category.equals("Image Creators"))) {
               continue;
            } else if(visualizer && !category.equals(GPConstants.TASK_TYPE_VISUALIZER) && !category.equals("Image Creators")) {
               continue;
            }
            List services = (List) categories2Tasks.get(category);
            if(services == null) {
               services = new ArrayList();
               categories2Tasks.put(category, services);
            }
            services.add(svc);
         }
         for(Iterator values = categories2Tasks.values().iterator(); values.hasNext(); ) {
            List services = (List) values.next();
            java.util.Collections.sort(services,
               new java.util.Comparator() {
                  public int compare(Object obj1, Object obj2) {
                     AnalysisService svc1 = (AnalysisService) obj1;
                     AnalysisService svc2 = (AnalysisService) obj2;
                     return svc1.getTaskInfo().getName().compareTo(
                           svc2.getTaskInfo().getName());
                  }


                  public boolean equals(Object obj1, Object obj2) {
                     AnalysisService svc1 = (AnalysisService) obj1;
                     AnalysisService svc2 = (AnalysisService) obj2;
                     return svc1.getTaskInfo().getName().equals(
                           svc2.getTaskInfo().getName());
                  }
               });

         }
         for(Iterator keys = categories2Tasks.keySet().iterator(); keys.hasNext(); ) {
            String category = (String) keys.next();
            category = Character.toUpperCase(category.charAt(0)) + category.substring(1, category.length());
            List services = (List) categories2Tasks.get(category);
            JMenu menu = null;
            if(!visualizer) {
               menu = new JMenu(category);
               add(menu);
            } else {
               menu = this;// FIXME
            }
            for(int i = 0; i < services.size(); i++) {
               AnalysisMenuItem mi = new AnalysisMenuItem((AnalysisService) services.get(i));
               mi.addActionListener(listener);
               menu.add(mi);
            }
         }

      }

   }


   static class AnalysisMenuItem extends JMenuItem {
      AnalysisService svc;


      public AnalysisMenuItem(AnalysisService svc) {
         super(svc.getTaskInfo().getName());
         this.svc = svc;
      }
   }


   class HelpMenu extends JMenu {
      public HelpMenu() {
         super("Help");

         add(
            new AbstractAction("About") {
               public void actionPerformed(ActionEvent e) {
                  GenePattern.showAbout();
               }
            });

         JMenuItem moduleColorKeyMenuItem = new JMenuItem("Module Color Key");
         add(moduleColorKeyMenuItem);
         moduleColorKeyMenuItem.addActionListener(
            new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  JPanel p = new JPanel();
                  p.setLayout(new GridLayout(3, 1));
                  JLabel colorKeyLabel = new JLabel("color key:");
                  JLabel yourTasksLabel = new JLabel("your tasks");
                  yourTasksLabel.setForeground(AUTHORITY_MINE_COLOR);
                  JLabel broadTasksLabel = new JLabel("Broad tasks");

                  JLabel otherTasksLabel = new JLabel("other tasks");
                  otherTasksLabel.setForeground(AUTHORITY_FOREIGN_COLOR);

                  p.add(yourTasksLabel);
                  p.add(broadTasksLabel);
                  p.add(otherTasksLabel);
                  Container c = new JPanel();
                  c.setLayout(new BorderLayout());
                  // c.add(colorKeyLabel, BorderLayout.NORTH);
                  c.add(p, BorderLayout.CENTER);

                  javax.swing.JScrollPane sp = new javax.swing.JScrollPane(c);
                  javax.swing.JOptionPane.showMessageDialog(GenePattern.getDialogParent(), sp, "Module Color Key", JOptionPane.INFORMATION_MESSAGE);
               }
            });

         add(
            new AbstractAction("Errors") {
               public void actionPerformed(ActionEvent e) {
                  GenePattern.showErrors();
               }
            });

         add(
            new AbstractAction("Warnings") {
               public void actionPerformed(ActionEvent e) {
                  GenePattern.showWarnings();
               }
            });
      }
   }



   class FileMenu extends JMenu {
      JobCompletedDialog jobCompletedDialog;
      AbstractAction changeServerAction;
      AbstractAction refreshAction;
      JFileChooser  projectDirFileChooser;
      
      public void setServerActionsEnabled(boolean b) {
         changeServerAction.setEnabled(b);
         refreshAction.setEnabled(b);
      }
      
      public FileMenu() {
         super("File");
         JMenuItem openProjectDirItem = new JMenuItem("Open Project Directory...");
         openProjectDirItem.setAccelerator(KeyStroke.getKeyStroke('O', MENU_SHORTCUT_KEY_MASK));
         openProjectDirItem.addActionListener(new ActionListener() {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  if(projectDirFileChooser==null) {
                     projectDirFileChooser = new JFileChooser();  
                     projectDirFileChooser.setDialogTitle("Choose a Project Directory");
                     projectDirFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                     projectDirFileChooser.setApproveButtonText("Select Directory");
                     projectDirFileChooser.setAccessory(new org.genepattern.gpge.ui.maindisplay.DirPreview(projectDirFileChooser));
                  }
                  if(projectDirFileChooser.showOpenDialog(GenePattern.getDialogParent())==JFileChooser.APPROVE_OPTION) {
                     File selectedFile = projectDirFileChooser.getSelectedFile();
                     if(!projectDirModel.contains(selectedFile)) {
                        projectDirModel.add(selectedFile);
                     }
                     // FIXME persist changes
                  }
               }
         });
         add(openProjectDirItem);
         jobCompletedDialog = new JobCompletedDialog();
         final javax.swing.JCheckBoxMenuItem showJobCompletedDialogMenuItem = new javax.swing.JCheckBoxMenuItem("Alert On Job Completion");
         showJobCompletedDialogMenuItem.setSelected(jobCompletedDialog.isShowingDialog());
         add(showJobCompletedDialogMenuItem);
         showJobCompletedDialogMenuItem.addActionListener(
            new ActionListener() {
               public void actionPerformed(ActionEvent e) {
                  jobCompletedDialog.setShowDialog(showJobCompletedDialogMenuItem.isSelected());
               }
            });
         
           
            
         changeServerAction = new javax.swing.AbstractAction("Server...") {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  
                  final ChangeServerDialog dialog = new ChangeServerDialog(MainFrame.this);
                  dialog.show(analysisServiceManager.getServer(), analysisServiceManager.getUsername(), new ActionListener() {
                     public void actionPerformed(ActionEvent e) {
                        dialog.dispose();
                        String server = dialog.getServer();
                        String username = dialog.getUsername();
                        try {
                           int port = Integer.parseInt(dialog.getPort());
                 
                           server = server + ":" + port;
                           if(!server.toLowerCase().startsWith("http://")) {
                              server = "http://" + server;
                           }
                           analysisServiceManager.disconnect();
                           analysisServiceManager = AnalysisServiceManager.getInstance(server, username);
                           refresh();
                           jobModel.removeAll();
                        } catch(NumberFormatException nfe) {
                           JOptionPane.showMessageDialog(GenePattern.getDialogParent(), "Invalid port. Please try again.");
                        }
                     }
                  });

               }
         };
         add(changeServerAction);
         changeServerAction.setEnabled(false);   
         refreshAction = new javax.swing.AbstractAction("Refresh") {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  refresh();
               }
            };
         refreshAction.setEnabled(false);   
         add(refreshAction);
       
         AbstractAction quitAction =
            new javax.swing.AbstractAction("Quit") {
               public void actionPerformed(java.awt.event.ActionEvent e) {
                  System.exit(0);
               }
            };
         quitAction.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke('Q', MENU_SHORTCUT_KEY_MASK));
         add(quitAction);

      }
   }
}
