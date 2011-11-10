//----------------------------------------------------------------------------//
//                                                                            //
//                     S y m b o l s C o n t r o l l e r                      //
//                                                                            //
//----------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">                          //
//  Copyright (C) Hervé Bitteur 2000-2011. All rights reserved.               //
//  This software is released under the GNU General Public License.           //
//  Goto http://kenai.com/projects/audiveris to report bugs or suggestions.   //
//----------------------------------------------------------------------------//
// </editor-fold>
package omr.glyph.ui;

import omr.glyph.SymbolsModel;
import omr.glyph.facets.Glyph;
import omr.glyph.text.TextRole;

import omr.log.Logger;

import omr.score.entity.Note;
import omr.score.entity.Text.CreatorText.CreatorType;
import omr.score.entity.TimeRational;

import omr.script.BoundaryTask;
import omr.script.RationalTask;
import omr.script.SegmentTask;
import omr.script.SlurTask;
import omr.script.TextTask;

import omr.sheet.BrokenLineContext;
import omr.sheet.SystemBoundary;
import omr.sheet.SystemInfo;

import omr.util.BrokenLine;
import omr.util.VerticalSide;

import org.jdesktop.application.Task;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Class <code>SymbolsController</code> is a GlyphsController specifically
 * meant for symbol glyphs, adding handling for assigning Texts, for fixing
 * Slurs and for segmenting on Stems.
 *
 * @author Hervé Bitteur
 */
public class SymbolsController
    extends GlyphsController
{
    //~ Static fields/initializers ---------------------------------------------

    /** Usual logger utility */
    private static final Logger logger = Logger.getLogger(
        SymbolsController.class);

    /** Color for hiding unknown glyphs when filter is ON */
    public static final Color hiddenColor = Color.white;

    //~ Constructors -----------------------------------------------------------

    //-------------------//
    // SymbolsController //
    //-------------------//
    /**
     * Create a handler dedicated to symbol glyphs
     *
     * @param model the related glyphs model
     */
    public SymbolsController (SymbolsModel model)
    {
        super(model);
    }

    //~ Methods ----------------------------------------------------------------

    //----------//
    // getModel //
    //----------//
    /**
     * Report the underlying model
     * @return the underlying glyphs model
     */
    @Override
    public SymbolsModel getModel ()
    {
        return (SymbolsModel) model;
    }

    //----------------------//
    // asyncAssignRationals //
    //----------------------//
    /**
     * Asynchronously assign a rational value to a collection of glyphs with
     * CUSTOM_TIME_SIGNATURE shape
     *
     * @param glyphs the impacted glyphs
     * @param timeRational the time sig rational value
     * @return the task that carries out the processing
     */
    public Task asyncAssignRationals (Collection<Glyph>  glyphs,
                                      final TimeRational timeRational)
    {
        return new RationalTask(sheet, timeRational, glyphs).launch(sheet);
    }

    //------------------//
    // asyncAssignTexts //
    //------------------//
    /**
     * Asynchronously assign text characteristics to a collection of textual
     * glyphs
     *
     * @param glyphs the impacted glyphs
     * @param textType the type of the creator, if relevant
     * @param textRole the role of this textual element
     * @param textContent the content as a string (if not empty)
     * @return the task that carries out the processing
     */
    public Task asyncAssignTexts (Collection<Glyph> glyphs,
                                  final CreatorType textType,
                                  final TextRole    textRole,
                                  final String      textContent)
    {
        return new TextTask(sheet, textType, textRole, textContent, glyphs).launch(
            sheet);
    }

    //--------------------//
    // asyncFixLargeSlurs //
    //--------------------//
    /**
     * Asynchronously fix a collection of glyphs as large slurs
     *
     * @param glyphs the slur glyphs to fix
     * @return the task that carries out the processing
     */
    public Task asyncFixLargeSlurs (Collection<Glyph> glyphs)
    {
        return new SlurTask(sheet, glyphs).launch(sheet);
    }

    //-----------------------//
    // asyncModifyBoundaries //
    //-----------------------//
    /**
     * Asynchronously perform a modification in systems boundaries
     * @param modifiedLines the set of modified lines
     * @return the task that carries out the processing
     */
    public Task asyncModifyBoundaries (Set<BrokenLine> modifiedLines)
    {
        List<BrokenLineContext> contexts = new ArrayList<BrokenLineContext>();

        // Retrieve impacted systems
        for (BrokenLine line : modifiedLines) {
            int above = 0;
            int below = 0;

            for (SystemInfo system : sheet.getSystems()) {
                SystemBoundary boundary = system.getBoundary();

                if (boundary.getLimit(VerticalSide.BOTTOM) == line) {
                    above = system.getId();
                } else if (boundary.getLimit(VerticalSide.TOP) == line) {
                    below = system.getId();
                }
            }

            contexts.add(new BrokenLineContext(above, below, line));
        }

        return new BoundaryTask(sheet, contexts).launch(sheet);

    }

    //--------------//
    // asyncSegment //
    //--------------//
    /**
     * Asynchronously segment a set of glyphs on their stems
     *
     * @param glyphs glyphs to segment in order to retrieve stems
     * @param isShort looking for short (or standard) stems
     * @return the task that carries out the processing
     */
    public Task asyncSegment (Collection<Glyph> glyphs,
                              final boolean     isShort)
    {
        return new SegmentTask(sheet, isShort, glyphs).launch(sheet);
    }

    //------------------//
    // showTranslations //
    //------------------//
    public void showTranslations (Collection<Glyph> glyphs)
    {
        for (Glyph glyph : glyphs) {
            for (Object entity : glyph.getTranslations()) {
                if (entity instanceof Note) {
                    Note note = (Note) entity;
                    logger.info(note + "->" + note.getChord());
                } else {
                    logger.info(entity.toString());
                }
            }
        }
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        return getClass()
                   .getSimpleName();
    }
}
