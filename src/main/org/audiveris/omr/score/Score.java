//------------------------------------------------------------------------------------------------//
//                                                                                                //
//                                           S c o r e                                            //
//                                                                                                //
//------------------------------------------------------------------------------------------------//
// <editor-fold defaultstate="collapsed" desc="hdr">
//
//  Copyright © Audiveris 2022. All rights reserved.
//
//  This program is free software: you can redistribute it and/or modify it under the terms of the
//  GNU Affero General Public License as published by the Free Software Foundation, either version
//  3 of the License, or (at your option) any later version.
//
//  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
//  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//  See the GNU Affero General Public License for more details.
//
//  You should have received a copy of the GNU Affero General Public License along with this
//  program.  If not, see <http://www.gnu.org/licenses/>.
//------------------------------------------------------------------------------------------------//
// </editor-fold>
package org.audiveris.omr.score;

import org.audiveris.omr.constant.Constant;
import org.audiveris.omr.constant.ConstantSet;
import org.audiveris.omr.sheet.Book;
import org.audiveris.omr.sheet.Sheet;
import org.audiveris.omr.sheet.SheetStub;
import org.audiveris.omr.util.Navigable;
import org.audiveris.omr.util.param.Param;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * Class <code>Score</code> represents a single movement, and is composed of one or
 * several instances of <code>Page</code> class.
 * <p>
 * The diagram below presents the roles of <code>Book</code> vs <code>Score</code>
 * and of <code>Sheet</code> vs <code>Page</code>:
 * <p>
 * The book at hand contains 3 sheets
 * (certainly because the input PDF or TIFF file contained 3 images):
 * <ol>
 * <li><code>Sheet</code> #1 begins with an indented system, which indicates the start of a movement
 * (named a <code>Score</code> by MusicXML).
 * There is no other indented system, so this <code>Sheet</code> contains a single
 * <code>Page</code>.
 * <li><code>Sheet</code> #2 exhibits an indentation for its system #3.
 * So, this indented system ends the previous score and starts a new one.
 * We have thus 2 <code>Page</code> instances in this <code>Sheet</code>.
 * <li><code>Sheet</code> #3 has no indented system and is thus composed of a single
 * <code>Page</code>
 * </ol>
 * <p>
 * To summarize, we have 2 scores in 3 sheets:
 * <ol>
 * <li><code>Score</code> #1, composed of:
 * <ol>
 * <li>single <code>Page</code> #1 of <code>Sheet</code> #1, followed by
 * <li><code>Page</code> #1 of <code>Sheet</code> #2
 * </ol>
 * <li><code>Score</code> #2, composed of:
 * <ol>
 * <li><code>Page</code> #2 of <code>Sheet</code> #2, followed by
 * <li>single <code>Page</code> #1 of <code>Sheet</code> #3
 * </ol>
 * </ol>
 * <img src="doc-files/Book-vs-Score.png" alt="Book-vs-Score UML">
 *
 * @author Hervé Bitteur
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name = "score")
public class Score
{
    //~ Static fields/initializers -----------------------------------------------------------------

    private static final Constants constants = new Constants();

    private static final Logger logger = LoggerFactory.getLogger(Score.class);

    /** Number of lines in a staff. */
    public static final int LINE_NB = 5;

    //~ Instance fields ----------------------------------------------------------------------------
    // Persistent data
    //----------------
    //
    /**
     * Score id, within containing book.
     * see {@link #getId()}.
     */
    /**
     * This is the list of <code>LogicalPart</code>'s defined for the whole score.
     */
    @XmlElement(name = "logical-part")
    private List<LogicalPart> logicalParts;

    /**
     * This is the list of references to score pages, as seen from this score.
     */
    @XmlElement(name = "page")
    private final List<ScorePageRef> pageLinks = new ArrayList<>();

    // Transient data
    //---------------
    //
    /** Containing book. */
    @Navigable(false)
    private Book book;

    /**
     * References to score pages, as seen from their containing sheet stubs.
     */
    private final ArrayList<PageRef> pageRefs = new ArrayList<>();

    /** Actual score pages. */
    private ArrayList<Page> pages;

    /** Handling of parts name and program. */
    private final Param<List<PartData>> partsParam;

    /** Handling of tempo parameter. */
    private final Param<Integer> tempoParam;

    /** The specified sound volume, if any. */
    private Integer volume;

    //~ Constructors -------------------------------------------------------------------------------
    /**
     * Create a Score.
     */
    public Score ()
    {
        partsParam = new PartsParam(this);

        tempoParam = new Param<>(this);
        tempoParam.setParent(Tempo.defaultTempo);
    }

    //~ Methods ------------------------------------------------------------------------------------
    //------------//
    // addPageRef //
    //------------//
    /**
     * Add a PageRef.
     *
     * @param stubNumber id of containing sheet stub
     * @param pageRef    to add
     */
    public void addPageRef (int stubNumber,
                            PageRef pageRef)
    {
        pageRefs.add(pageRef);
        pageLinks.add(new ScorePageRef(stubNumber, pageRef.getId()));
    }

    //-------//
    // close //
    //-------//
    /**
     * Close this score instance, as well as its view if any.
     */
    public void close ()
    {
        logger.info("Closing {}", this);
    }

    /**
     * Report the containing book for this score
     *
     * @return the book
     */
    public Book getBook ()
    {
        return book;
    }

    //---------//
    // setBook //
    //---------//
    /**
     * Assign the containing book.
     *
     * @param book the book to set
     */
    public void setBook (Book book)
    {
        this.book = book;
    }

    //--------------//
    // getFirstPage //
    //--------------//
    /**
     * Report the first page in this score
     *
     * @return first page
     */
    public Page getFirstPage ()
    {
        if (pageRefs.isEmpty()) {
            return null;
        }

        return getPage(pageRefs.get(0));
    }

    //-----------------//
    // getFirstPageRef //
    //-----------------//
    /**
     * Return the first PageRef in this score
     *
     * @return first pageRef
     */
    public PageRef getFirstPageRef ()
    {
        if (pageRefs.isEmpty()) {
            return null;
        }

        return pageRefs.get(0);
    }

    //------------------//
    // getFollowingPage //
    //------------------//
    /**
     * Report the page, if any, that follows the provided page within containing score.
     *
     * @param page the provided page
     * @return the following page or null
     */
    public Page getFollowingPage (Page page)
    {
        int index = getPageIndex(page);

        if (index < (pageRefs.size() - 1)) {
            return getPage(pageRefs.get(index + 1));
        }

        return null;
    }

    //-------//
    // getId //
    //-------//
    /**
     * The score ID is the rank, starting at 1, of this <code>score</code>
     * in the containing <code>book</code>.
     *
     * @return the score id
     */
    @XmlAttribute(name = "id")
    public Integer getId ()
    {
        final int index = book.getScores().indexOf(this);

        if (index != -1) {
            return 1 + index;
        }

        return null;
    }

    //-------------//
    // getLastPage //
    //-------------//
    /**
     * Report the last page in this score.
     *
     * @return last page
     */
    public Page getLastPage ()
    {
        if (pageRefs.isEmpty()) {
            return null;
        }

        return getPage(pageRefs.get(pageRefs.size() - 1));
    }

    //----------------//
    // getLastPageRef //
    //----------------//
    /**
     * Report the last PageRef in this score.
     *
     * @return last pageRef
     */
    public PageRef getLastPageRef ()
    {
        if (pageRefs.isEmpty()) {
            return null;
        }

        return pageRefs.get(pageRefs.size() - 1);
    }

    //-----------------//
    // getLogicalParts //
    //-----------------//
    /**
     * Report the score list of logical parts.
     *
     * @return partList the list of logical parts
     */
    public List<LogicalPart> getLogicalParts ()
    {
        return logicalParts;
    }

    //-----------------//
    // setLogicalParts //
    //-----------------//
    /**
     * Assign a part list valid for the whole score.
     *
     * @param logicalParts the list of logical parts
     */
    public void setLogicalParts (List<LogicalPart> logicalParts)
    {
        this.logicalParts = logicalParts;
    }

    //--------------------//
    // getMeasureIdOffset //
    //--------------------//
    /**
     * Report the offset to add to page-based measure IDs of the provided page to get
     * absolute (score-based) IDs.
     *
     * @param page the provided page
     * @return the measure id offset for the page
     */
    public Integer getMeasureIdOffset (Page page)
    {
        final PageRef ref = getPageRef(page);
        int offset = 0;

        for (PageRef pageRef : pageRefs) {
            if (pageRef == ref) {
                return offset;
            }

            // Beware of page with no deltaMeasureId (because its transcription failed)
            Integer delta = pageRef.getDeltaMeasureId();

            if (delta != null) {
                offset += delta;
            } else {
                logger.info("No deltaMeasureId for {}", pageRef);
            }
        }

        return offset;
    }

    //---------//
    // getPage //
    //---------//
    /**
     * Report the page at provided 1-based number
     *
     * @param number 1-based number in score
     * @return the corresponding page
     */
    public Page getPage (int number)
    {
        final int index = number - 1;

        if ((index < 0) || (index >= pageRefs.size())) {
            throw new IllegalArgumentException("No page with number " + number);
        }

        return getPage(pageRefs.get(index));
    }

    //--------------//
    // getPageCount //
    //--------------//
    /**
     * Report the number of pages in score.
     *
     * @return number of pages
     */
    public int getPageCount ()
    {
        return pageRefs.size();
    }

    //--------------//
    // getPageIndex //
    //--------------//
    /**
     * Report index of the provided page in the score sequence of pages.
     *
     * @param page the provided page
     * @return the page index in score, or -1 if not found
     */
    public int getPageIndex (Page page)
    {
        final PageRef ref = getPageRef(page);

        return pageRefs.indexOf(ref);
    }

    //------------//
    // getPageRef //
    //------------//
    /**
     * Return the score pageRef for a specified sheet stub.
     *
     * @param sheetNumber sheet stub number
     * @return the score page in this sheet, or null
     */
    public PageRef getPageRef (int sheetNumber)
    {
        for (PageRef pageRef : pageRefs) {
            if (pageRef.getSheetNumber() == sheetNumber) {
                return pageRef;
            }
        }

        return null;
    }

    //-------------//
    // getPageRefs //
    //-------------//
    /**
     * Report the sequence of PageRef instances for this score.
     *
     * @return sequence of PageRef's
     */
    public List<PageRef> getPageRefs ()
    {
        return Collections.unmodifiableList(pageRefs);
    }

    //----------//
    // getPages //
    //----------//
    /**
     * Report the collection of pages in that score.
     *
     * @return the pages
     */
    public List<Page> getPages ()
    {
        if (pages == null) {
            pages = new ArrayList<>();

            // De-reference pageRefs
            for (PageRef ref : pageRefs) {
                pages.add(getPage(ref));
            }
        }

        return pages;
    }

    //----------//
    // getPages //
    //----------//
    /**
     * Report the collection of pages in that score, limited to the provided stubs.
     *
     * @param stubs valid selected stubs
     * @return the relevant pages
     */
    public List<Page> getPages (List<SheetStub> stubs)
    {
        final List<Page> relevantPages = new ArrayList<>();

        // De-reference pageRefs
        for (PageRef ref : pageRefs) {
            final SheetStub stub = book.getStub(ref.getSheetNumber());

            if (stubs.contains(stub)) {
                final Sheet sheet = stub.getSheet();
                final Page page = sheet.getPages().get(ref.getId() - 1);
                relevantPages.add(page);
            }
        }

        return relevantPages;
    }

    //---------------//
    // getPartsParam //
    //---------------//
    /**
     * Report the sequence of parts parameters.
     *
     * @return sequence of parts parameters (name, midi program)
     */
    public Param<List<PartData>> getPartsParam ()
    {
        return partsParam;
    }

    //------------------//
    // getPrecedingPage //
    //------------------//
    /**
     * Report the page, if any, that precedes the provided page within containing score.
     *
     * @param page the provided page
     * @return the preceding page or null
     */
    public Page getPrecedingPage (Page page)
    {
        int index = getPageIndex(page);

        if (index > 0) {
            return getPage(pageRefs.get(index - 1));
        }

        return null;
    }

    //----------------//
    // getSheetPageId //
    //----------------//
    /**
     * Report the local page ID for this score in provided sheet
     *
     * @param sheetNumber provided sheet number
     * @return ID of score page in provided sheet
     */
    public Integer getSheetPageId (int sheetNumber)
    {
        final PageRef pageRef = getPageRef(sheetNumber);

        if (pageRef != null) {
            return pageRef.getId();
        }

        return null;
    }

    //----------//
    // getStubs //
    //----------//
    /**
     * Report the sequence of stubs this score is made from.
     *
     * @return the list of relevant stubs
     */
    public List<SheetStub> getStubs ()
    {
        final List<SheetStub> pageStubs = new ArrayList<>();
        final List<SheetStub> bookStubs = book.getStubs();

        for (PageRef ref : pageRefs) {
            pageStubs.add(bookStubs.get(ref.getSheetNumber() - 1));
        }

        return pageStubs;
    }

    //----------------//
    // getTempoParam //
    //---------------//
    /**
     * Report the tempo parameter.
     *
     * @return tempo information
     */
    public Param<Integer> getTempoParam ()
    {
        return tempoParam;
    }

    //-----------//
    // getVolume //
    //-----------//
    /**
     * Report the assigned volume, if any.
     * If the value is not yet set, it is set to the default value and returned.
     *
     * @return the assigned volume, or null
     */
    public Integer getVolume ()
    {
        if (!hasVolume()) {
            volume = getDefaultVolume();
        }

        return volume;
    }

    //-----------//
    // setVolume //
    //-----------//
    /**
     * Assign a volume value.
     *
     * @param volume the volume value to be assigned
     */
    public void setVolume (Integer volume)
    {
        this.volume = volume;
    }

    //-----------//
    // hasVolume //
    //-----------//
    /**
     * Check whether a volume has been defined for this score.
     *
     * @return true if a volume is defined
     */
    public boolean hasVolume ()
    {
        return volume != null;
    }

    //---------//
    // isFirst //
    //---------//
    /**
     * Report whether the provided page is the first page in score.
     *
     * @param page the provided page
     * @return true if first
     */
    public boolean isFirst (Page page)
    {
        PageRef pageRef = getPageRef(page);

        if (pageRef != null) {
            return pageRefs.get(0) == pageRef;
        }

        return false;
    }

    //-------------//
    // isMultiPage //
    //-------------//
    /**
     * @return the multiPage
     */
    public boolean isMultiPage ()
    {
        return pageRefs.size() > 1;
    }

    //----------//
    // toString //
    //----------//
    @Override
    public String toString ()
    {
        return "{Score " + getId() + "}";
    }

    //----------------//
    // afterUnmarshal //
    //----------------//
    @SuppressWarnings("unused")
    private void afterUnmarshal (Unmarshaller u,
                                 Object parent)
    {
        for (ScorePageRef pageLink : pageLinks) {
            SheetStub stub = book.getStub(pageLink.sheetNumber);

            if (pageLink.sheetPageId > 0) {
                if (stub.getPageRefs().size() >= pageLink.sheetPageId) {
                    pageRefs.add(stub.getPageRefs().get(pageLink.sheetPageId - 1));
                } else {
                    logger.info("Missing pages in {}", stub);
                }
            } else {
                logger.info("Illegal pageLink.sheetPageId: {}", pageLink.sheetPageId);
            }
        }
    }

    //-----------------//
    // beforeUnmarshal //
    //-----------------//
    @SuppressWarnings("unused")
    private void beforeUnmarshal (Unmarshaller u,
                                  Object parent)
    {
        book = (Book) parent;
    }

    //---------//
    // getPage //
    //---------//
    private Page getPage (PageRef ref)
    {
        Sheet sheet = book.getStubs().get(ref.getSheetNumber() - 1).getSheet();

        return sheet.getPages().get(ref.getId() - 1);
    }

    //------------//
    // getPageRef //
    //------------//
    private PageRef getPageRef (Page page)
    {
        final int sheetNumber = page.getSheet().getStub().getNumber();

        for (PageRef pageRef : pageRefs) {
            if (pageRef.getSheetNumber() == sheetNumber) {
                return pageRef;
            }
        }

        logger.error("No page ref for " + page);

        return null;
    }

    //------------------//
    // getDefaultVolume //
    //------------------//
    /**
     * Report default value for Midi volume.
     *
     * @return the default volume value
     */
    public static int getDefaultVolume ()
    {
        return constants.defaultVolume.getValue();
    }

    //------------------//
    // setDefaultVolume //
    //------------------//
    /**
     * Assign default value for Midi volume.
     *
     * @param volume the default volume value
     */
    public static void setDefaultVolume (int volume)
    {
        constants.defaultVolume.setValue(volume);
    }

    //-----------------//
    // setDefaultTempo //
    //-----------------//
    /**
     * Assign default value for Midi tempo.
     *
     * @param tempo the default tempo value
     */
    public static void setDefaultTempo (int tempo)
    {
        constants.defaultTempo.setValue(tempo);
    }

    //~ Inner Classes ------------------------------------------------------------------------------
    //-----------//
    // Constants //
    //-----------//
    private static class Constants
            extends ConstantSet
    {

        private final Constant.Integer defaultTempo = new Constant.Integer(
                "QuartersPerMn",
                120,
                "Default tempo, stated in number of quarters per minute");

        private final Constant.Integer defaultVolume = new Constant.Integer(
                "Volume",
                78,
                "Default Volume in 0..127 range");
    }

    //------------//
    // PartsParam //
    //------------//
    private class PartsParam
            extends Param<List<PartData>>
    {

        public PartsParam (Object scope)
        {
            super(scope);
        }

        @Override
        public List<PartData> getSpecific ()
        {
            List<LogicalPart> list = getLogicalParts();

            if (list != null) {
                List<PartData> data = new ArrayList<>();

                for (LogicalPart logicalPart : list) {
                    // Initial setting for part midi program
                    int prog = (logicalPart.getMidiProgram() != null)
                            ? logicalPart.getMidiProgram() : logicalPart.getDefaultProgram();

                    data.add(new PartData(logicalPart.getName(), prog));
                }

                return data;
            } else {
                return null;
            }
        }

        @Override
        public boolean setSpecific (List<PartData> specific)
        {
            try {
                for (int i = 0; i < specific.size(); i++) {
                    PartData data = specific.get(i);
                    LogicalPart logicalPart = getLogicalParts().get(i);

                    // Part name
                    logicalPart.setName(data.name);

                    // Part midi program
                    logicalPart.setMidiProgram(data.program);
                }

                logger.info("Score parts have been updated");

                return true;
            } catch (Exception ex) {
                logger.warn("Error updating score parts", ex);
            }

            return false;
        }
    }
}
