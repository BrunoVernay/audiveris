//----------------------------------------------------------------------------//
//                                                                            //
//                        S y s t e m s B u i l d e r                         //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.sheet;

import omr.Main;

import omr.check.CheckBoard;
import omr.check.CheckSuite;

import omr.constant.Constant;
import omr.constant.ConstantSet;

import omr.glyph.GlyphsModel;
import omr.glyph.Nest;
import omr.glyph.facets.Glyph;
import omr.glyph.ui.BarMenu;
import omr.glyph.ui.GlyphsController;
import omr.glyph.ui.NestView;

import omr.grid.StaffInfo;
import omr.grid.SystemManager;

import omr.log.Logger;

import omr.script.BoundaryTask;

import omr.selection.GlyphEvent;
import omr.selection.MouseMovement;
import omr.selection.SelectionService;
import omr.selection.UserEvent;

import omr.sheet.BarsChecker.BarCheckSuite;

import omr.step.Step;
import omr.step.StepException;
import omr.step.Steps;

import omr.util.BrokenLine;
import omr.util.VerticalSide;

import org.jdesktop.application.Task;

import java.awt.Point;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Class <code>SystemsBuilder</code> is in charge of retrieving the systems
 * (SystemInfo instances) and parts (PartInfo instances) in the provided sheet
 * and to allocate the corresponding instances on the Score side (the Score
 * instance, and the various instances of ScoreSystem, SystemPart and Staff).
 * The result is visible in the ScoreView.
 *
 * <p>Is does so automatically by using barlines glyphs that embrace staves,
 * parts and systems.  It also allows the user to interactively modify the
 * retrieved information.</p>
 *
 * <p>Systems define their own area, which may be more complex than a simple
 * ordinate range, in order to precisely define which glyph belongs to which
 * system. The user has the ability to interactively modify the broken line
 * that defines the limit between two adjacent systems.</p>
 *
 * <p>This class has close relationships with {@link MeasuresBuilder} in charge
 * of building and checking the measures, because barlines are used both to
 * define systems and parts, and to define measures.</p>
 *
 * <p>From the related view, the user has the ability to assign or to deassign
 * a barline glyph, with subsequent impact on the related measures.</p>
 *
 * <p>TODO: Implement a way for the user to tell whether a bar glyph is or not
 * a BAR_PART_DEFINING (i.e. if it is anchored on top and bottom).</p>
 *
 * @author Hervé Bitteur
 */
public class SystemsBuilder
    extends GlyphsModel
{
    //~ Static fields/initializers ---------------------------------------------

    /** Specific application parameters */
    private static final Constants constants = new Constants();

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(SystemsBuilder.class);

    /** Events this entity is interested in */
    private static final Class[] eventClasses = new Class[] { GlyphEvent.class };

    //~ Instance fields --------------------------------------------------------

    /** Companion physical stick barsChecker */
    private final BarsChecker barsChecker;

    /** View on bars, if so desired */
    private NestView sceneView;

    /** Sheet retrieved systems */
    private final List<SystemInfo> systems;

    //~ Constructors -----------------------------------------------------------

    //----------------//
    // SystemsBuilder //
    //----------------//
    /**
     * Creates a new SystemsBuilder object.
     *
     * @param sheet the related sheet
     */
    public SystemsBuilder (Sheet sheet)
    {
        super(sheet, sheet.getNest(), Steps.valueOf(Steps.SPLIT));

        systems = sheet.getSystems();

        // BarsChecker companion, in charge of purely physical tests
        barsChecker = new BarsChecker(sheet, false);
    }

    //~ Methods ----------------------------------------------------------------

    //--------------//
    // buildSystems //
    //--------------//
    /**
     * Process the sheet information produced by the GRID step and allocate the
     * related score information
     */
    public void buildSystems ()
        throws StepException
    {
        try {
            doBuildSystems();
        } finally {
            // Provide use checkboard for barlines
            if (Main.getGui() != null) {
                sheet.getAssembly()
                     .addBoard(
                    Step.DATA_TAB,
                    new BarCheckBoard(
                        barsChecker.getSuite(),
                        nest.getGlyphService(),
                        eventClasses));
            }
        }
    }

    //-------------------//
    // rebuildAllSystems //
    //-------------------//
    public void rebuildAllSystems ()
    {
        // Update the retrieved systems
        try {
            doBuildSystems();
        } catch (StepException ex) {
            logger.warning("Error rebuilding systems info", ex);
        }

        // Update the view accordingly
        if (sceneView != null) {
            sceneView.repaint();
        }
    }

    //---------------//
    // useBoundaries //
    //---------------//
    public SortedSet<SystemInfo> useBoundaries ()
    {
        // Split the entities (horizontals sections, vertical sections,
        // vertical sticks) to the system they belong to.
        return splitSystemEntities();
    }

    //------------------------//
    // allocateScoreStructure //
    //------------------------//
    /**
     * For each SystemInfo, build the corresponding System entity with all its
     * depending Parts and Staves
     */
    private void allocateScoreStructure ()
        throws StepException
    {
        // Clear Score -> Systems
        sheet.getPage()
             .resetSystems();

        for (SystemInfo system : systems) {
            system.allocateScoreStructure(); // ScoreSystem, Parts & Staves
        }
    }

    //------------//
    // buildParts //
    //------------//
    /**
     * Knowing the starting staff indice of each part, allocate the related
     * PartInfo instances in proper SystemInfo instances
     */
    private void buildParts ()
    {
        final SystemManager systemManager = sheet.getSystemManager();
        final Integer[]     partTops = systemManager.getPartTops();

        for (SystemInfo system : systemManager.getSystems()) {
            int      partTop = -1;
            PartInfo part = null;

            for (StaffInfo staff : system.getStaves()) {
                int topId = partTops[staff.getId() - 1];

                if (topId != partTop) {
                    part = new PartInfo();
                    system.addPart(part);
                    partTop = topId;
                }

                part.addStaff(staff);
            }
        }

        // TODO   // Specific degraded case, just one staff, no bar stick
        //        if (systems.isEmpty() && (staffNb == 1)) {
        //            StaffInfo singleStaff = staffManager.getFirstStaff();
        //            system = new SystemInfo(++id, sheet);
        //            systems.add(system);
        //            system.addStaff(singleStaff);
        //            part = new PartInfo();
        //            system.addPart(part);
        //            part.addStaff(singleStaff);
        //            logger.warning("Created one system, one part, one staff");
        //        }
        if (logger.isFineEnabled()) {
            for (SystemInfo systemInfo : systems) {
                Main.dumping.dump(systemInfo);

                int i = 0;

                for (PartInfo partInfo : systemInfo.getParts()) {
                    Main.dumping.dump(partInfo, "Part #" + ++i, 1);
                }
            }
        }
    }

    //----------------//
    // doBuildSystems //
    //----------------//
    private void doBuildSystems ()
        throws StepException
    {
        // Systems have been created by GRID step on sheet side
        // Build parts on sheet side
        buildParts();

        // Create score counterparts
        // Build systems, parts & measures on score side
        allocateScoreStructure();

        // Report number of systems retrieved
        reportResults();

        // Define precisely the systems boundaries
        sheet.computeSystemBoundaries();

        useBoundaries();
    }

    //---------------//
    // reportResults //
    //---------------//
    private void reportResults ()
    {
        StringBuilder sb = new StringBuilder();
        int           partNb = 0;

        for (SystemInfo system : sheet.getSystems()) {
            partNb = Math.max(partNb, system.getParts().size());
        }

        int sysNb = systems.size();

        if (partNb > 0) {
            sb.append(partNb)
              .append(" part");

            if (partNb > 1) {
                sb.append("s");
            }
        } else {
            sb.append("no part found");
        }

        sheet.getBench()
             .recordPartCount(partNb);

        if (sysNb > 0) {
            sb.append(", ")
              .append(sysNb)
              .append(" system");

            if (sysNb > 1) {
                sb.append("s");
            }
        } else {
            sb.append(", no system found");
        }

        sheet.getBench()
             .recordSystemCount(sysNb);

        logger.info(sheet.getLogPrefix() + sb.toString());
    }

    //---------------------//
    // splitSystemEntities //
    //---------------------//
    /**
     * Split horizontals, vertical sections, glyphs per system
     * @return the set of modified systems
     */
    private SortedSet<SystemInfo> splitSystemEntities ()
    {
        // Split everything, including horizontals, per system
        SortedSet<SystemInfo> modifiedSystems = new TreeSet<SystemInfo>();
        ///modifiedSystems.addAll(sheet.splitHorizontals());
        modifiedSystems.addAll(sheet.splitHorizontalSections());
        modifiedSystems.addAll(sheet.splitVerticalSections());
        modifiedSystems.addAll(sheet.splitBarSticks(nest.getAllGlyphs()));

        if (!modifiedSystems.isEmpty()) {
            StringBuilder sb = new StringBuilder("[");

            for (SystemInfo system : modifiedSystems) {
                sb.append("#")
                  .append(system.getId());
            }

            sb.append("]");

            if (logger.isFineEnabled()) {
                logger.info(sheet.getLogPrefix() + "Impacted systems: " + sb);
            }
        }

        return modifiedSystems;
    }

    //~ Inner Classes ----------------------------------------------------------

//    //----------------//
//    // BarsController //
//    //----------------//
//    /**
//     * A glyphs controller specifically meant for barlines
//     */
//    public class BarsController
//        extends GlyphsController
//    {
//        //~ Constructors -------------------------------------------------------
//
//        public BarsController ()
//        {
//            super(SystemsBuilder.this);
//        }
//
//        //~ Methods ------------------------------------------------------------
//
//        public Task asyncModifyBoundaries (final Set<SystemInfo> modifiedSystems)
//        {
//            if (logger.isFineEnabled()) {
//                logger.fine(
//                    "asyncModifyBoundaries " + " modifiedSystems:" +
//                    modifiedSystems);
//            }
//
//            // Retrieve containing system
//            for (SystemInfo system : modifiedSystems) {
//                SystemBoundary boundary = system.getBoundary();
//
//                for (VerticalSide side : VerticalSide.values()) {
//                    if (boundary.getLimit(side) == brokenLine) {
//                        return new BoundaryTask(system, side, brokenLine).launch(
//                            sheet);
//                    }
//                }
//            }
//
//            return null;
//        }
//    }

    //-----------//
    // Constants //
    //-----------//
    private static final class Constants
        extends ConstantSet
    {
        //~ Instance fields ----------------------------------------------------

        Scale.Fraction  maxDeltaLength = new Scale.Fraction(
            0.2,
            "Maximum difference in run length to be part of the same section");

        /** Maximum cotangent for checking a barline candidate */
        Constant.Double maxCoTangentForCheck = new Constant.Double(
            "cotangent",
            0.1,
            "Maximum cotangent for checking a barline candidate");
        Scale.Fraction  maxBarThickness = new Scale.Fraction(
            1.0,
            "Maximum thickness of an interesting vertical stick");
    }

    //---------------//
    // BarCheckBoard //
    //---------------//
    /**
     * A specific board dedicated to physical checks of bar sticks
     */
    private class BarCheckBoard
        extends CheckBoard<BarsChecker.GlyphContext>
    {
        //~ Constructors -------------------------------------------------------

        public BarCheckBoard (CheckSuite<BarsChecker.GlyphContext> suite,
                              SelectionService                     eventService,
                              Class[]                              eventList)
        {
            super("Barline", suite, eventService, eventList);
        }

        //~ Methods ------------------------------------------------------------

        @Override
        public void onEvent (UserEvent event)
        {
            try {
                // Ignore RELEASING
                if (event.movement == MouseMovement.RELEASING) {
                    return;
                }

                if (event instanceof GlyphEvent) {
                    BarsChecker.GlyphContext context = null;
                    GlyphEvent               glyphEvent = (GlyphEvent) event;
                    Glyph                    glyph = glyphEvent.getData();

                    if (glyph != null) {
                        // Make sure this is a rather vertical stick
                        if (Math.abs(glyph.getInvertedSlope()) <= constants.maxCoTangentForCheck.getValue()) {
                            // To get a fresh suite
                            setSuite(barsChecker.new BarCheckSuite());
                            context = new BarsChecker.GlyphContext(glyph);
                        }
                    }

                    tellObject(context);
                }
            } catch (Exception ex) {
                logger.warning(getClass().getName() + " onEvent error", ex);
            }
        }
    }
//
//    //--------//
//    // MyView //
//    //--------//
//    private final class MyView
//        extends NestView
//    {
//        //~ Instance fields ----------------------------------------------------
//
//        /** Popup menu related to glyph selection */
//        private BarMenu barMenu;
//
//        //~ Constructors -------------------------------------------------------
//
//        //        /** Acceptable distance since last reference point (while dragging) */
//        //        private int maxDraggingDelta = (int) Math.rint(
//        //            constants.draggingRatio.getValue() * BrokenLine.getDefaultStickyDistance());
//        //
//        //        // Latest designated reference point, if any */
//        //        private Point      lastPoint = null;
//        //
//        //        // Latest information, meaningful only if lastPoint is not null */
//        //        private BrokenLine lastLine = null;
//        private MyView (Nest           nest,
//                        BarsController barsController)
//        {
//            super(
//                nest,
//                barsController,
//                Arrays.asList(sheet.getHorizontalLag(), sheet.getVerticalLag()));
//            setName("SystemsBuilder-View");
//
//            setLocationService(sheet.getLocationService());
//
//            barMenu = new BarMenu(getController());
//        }
//
//        //~ Methods ------------------------------------------------------------
//
//        //-----------------//
//        // contextSelected //
//        //-----------------//
//        @Override
//        public void contextSelected (Point         pt,
//                                     MouseMovement movement)
//        {
//            // Retrieve the selected glyphs
//            Set<Glyph> glyphs = nest.getSelectedGlyphSet();
//
//            // To display point information
//            if ((glyphs == null) || glyphs.isEmpty()) {
//                pointSelected(pt, movement); // This may change glyph selection
//                glyphs = nest.getSelectedGlyphSet();
//            }
//
//            if ((glyphs != null) && !glyphs.isEmpty()) {
//                // Update the popup menu according to selected glyphs
//                barMenu.updateMenu();
//
//                // Show the popup menu
//                barMenu.getMenu()
//                       .getPopupMenu()
//                       .show(
//                    this,
//                    getZoom().scaled(pt.x),
//                    getZoom().scaled(pt.y));
//            } else {
//                // Popup with no glyph selected ?
//            }
//        }
//
//        //        //---------//
//        //        // onEvent //
//        //        //---------//
//        //        /**
//        //         * Notification about selection objects
//        //         * @param event the notified event
//        //         */
//        //        @Override
//        //        public void onEvent (UserEvent event)
//        //        {
//        //            try {
//        //                // Default lag view behavior, including specifics
//        //                if (event.movement != MouseMovement.RELEASING) {
//        //                    super.onEvent(event);
//        //                }
//        //
//        //                if (event instanceof LocationEvent) {
//        //                    //                    // Update system boundary?
//        //                    //                    LocationEvent sheetLocation = (LocationEvent) event;
//        //                    //
//        //                    //                    if (sheetLocation.hint == SelectionHint.LOCATION_INIT) {
//        //                    //                        Rectangle rect = sheetLocation.rectangle;
//        //                    //
//        //                    //                        if (rect != null) {
//        //                    //                            if (event.movement != MouseMovement.RELEASING) {
//        //                    //                                // While user is dragging, simply modify the line
//        //                    //                                updateBoundary(
//        //                    //                                    new Point(
//        //                    //                                        rect.x + (rect.width / 2),
//        //                    //                                        rect.y + (rect.height / 2)));
//        //                    //                            } else if (lastLine != null) {
//        //                    //                                // User has released the mouse with a known line
//        //                    //
//        //                    //                                // Perform boundary modifs synchronously
//        //                    //                                Set<SystemInfo> modifs = splitSystemEntities();
//        //                    //
//        //                    //                                // If modifs, launch updates asynchronously
//        //                    //                                ///if (!modifs.isEmpty()) {
//        //                    //                                barsController.asyncModifyBoundaries(
//        //                    //                                    lastLine,
//        //                    //                                    modifs);
//        //                    //                                lastPoint = null;
//        //                    //                                lastLine = null;
//        //                    //
//        //                    //                                ///}
//        //                    //                            }
//        //                    //                        }
//        //                    //                    }
//        //                }
//        //            } catch (Exception ex) {
//        //                logger.warning(getClass().getName() + " onEvent error", ex);
//        //            }
//        //        }
//        //
//        //        //-------------//
//        //        // renderItems //
//        //        //-------------//
//        //        @Override
//        //        public void renderItems (Graphics2D g)
//        //        {
//        //            // Render all physical info known so far, which is just the staff
//        //            // line info, lineset by lineset
//        //            sheet.getPage()
//        //                 .accept(new SheetPainter(g, true));
//        //
//        //            super.renderItems(g);
//        //        }
//    }
}
