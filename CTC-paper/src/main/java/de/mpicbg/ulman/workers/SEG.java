/*
 * CC BY-SA 4.0
 *
 * The code is licensed with "Attribution-ShareAlike 4.0 International license".
 * See the license details:
 *     https://creativecommons.org/licenses/by-sa/4.0/
 *
 * Copyright (C) 2017 Martin Maška, Vladimír Ulman
 */
package de.mpicbg.ulman.workers;

import org.scijava.log.LogService;

import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.view.Views;
import net.imglib2.type.numeric.integer.UnsignedShortType;

import io.scif.img.ImgIOException;
import java.io.IOException;

import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.Iterator;

import de.mpicbg.ulman.workers.TrackDataCache.TemporalLevel;

public class SEG
{
	///shortcuts to some Fiji services
	private final LogService log;

	///a constructor requiring connection to Fiji report/log services
	public SEG(final LogService _log)
	{
		//check that non-null was given for _log!
		if (_log == null)
			throw new NullPointerException("No log service supplied.");

		log = _log;
	}

	/**
	 * Calculation option: do report list of discrepancies between the reference
	 * and computed tracking result.
	 * This is helpful for algorithm developers as it shows what, where and when
	 * was incorrect in their results.
	 */
	public boolean doLogReports = false;

	// ----------- the SEG essentially starts here -----------
	//auxiliary data:

	///the to-be-calculated measure value
	private double seg = 0.0;


	//---------------------------------------------------------------------/
	/**
	 * This is the main SEG calculator.
	 */
	public double calculate(final String gtPath, final String resPath)
	throws IOException, ImgIOException
	{
		log.info(" GT path: "+gtPath+"/SEG");
		log.info("RES path: "+resPath);

		//instantiate the cache because it has functions we will use
		final TrackDataCache cache = new TrackDataCache(log);

		//do the bottom stage
		//DEBUG//log.info("Computing the SEG completely...");
		seg = 0.0;
		long counter = 0;

		//scan the SEG folder to get a list of files to process,
		//NB: the processing order of the files is not important
		final PathMatcher fileMatcher
			= FileSystems.getDefault().getPathMatcher("glob:man_seg*");
		@SuppressWarnings("resource")
		final Stream<Path> fileList
			= Files.list(Paths.get(gtPath+"/SEG"));

		//list file by file and process...
		Iterator<Path> files = fileList.iterator();
		while (files.hasNext())
		{
			final Path file = files.next();

			//check the file is of proper file name and not a folder
			if (!fileMatcher.matches(file.getFileName())) continue;
			if (!Files.isRegularFile(file)) continue;

			//we have likely the right file,
			//extract timepoint and possibly also the slice number
			int time  = -1;
			int slice = -1;

			//with or with out a slice information?
			final String filename = file.getFileName().toString();
			if (filename.charAt(7) == '_')
			{
				//with slice info
				time = Integer.parseInt(filename.substring(8, 11));
				if (filename.charAt(11) == '_')
					slice = Integer.parseInt(filename.substring(12, 15));
			}
			else
			{
				//no slice info
				time = Integer.parseInt(filename.substring(7, 10));
			}

			//time point number must have been parsed, or in trouble...
			if (time < 0)
				throw new IllegalArgumentException("Error extracting time point information"
					+" from file "+filename+"!");

			//read the image pair
			IterableInterval<UnsignedShortType> gt_img
				= cache.ReadImageG16(file.toString());

			RandomAccessibleInterval<UnsignedShortType> res_img
				= cache.ReadImageG16(String.format("%s/mask%03d.tif",resPath,time));

			//check that slice "extracting" can make sense (the 3rd dim must be present)
			if (slice > -1 && res_img.numDimensions() <= 2)
				throw new IllegalArgumentException("GT image at time "+time
					+" specifies slice but the image is not 3D.");

			/*
			for (int n=0; n < 2; ++n)
				if (gt_img.dimension(n) != res_img.dimension(n))
					throw new IllegalArgumentException("Image pair at time"+time
						+" does not consist of images of the same x,y size.");
			*/

			//should extract slice? use imglib2 views instead
			if (slice > -1)
			{
				res_img = Views.hyperSlice(res_img, 2, slice);
				log.info("Considering only slice "+slice);
			}

			//now, both images must of the same size...
			for (int n=0; n < gt_img.numDimensions(); ++n)
				if (gt_img.dimension(n) != res_img.dimension(n))
					throw new IllegalArgumentException("Image pair at time"+time
						+" does not consist of images of the same size.");

			cache.ClassifyLabels(gt_img, res_img);

			//after ClassifyLabels(), the voxel-matching info is here:
			final TemporalLevel level = cache.levels.lastElement();

			//calculate Jaccard for matching markers at this 'level'/time point
			if (doLogReports)
				log.info("----------T="+time+" Z="+(slice==-1?0:slice)+"----------");

			//over all GT labels
			final int m_match_lineSize = level.m_gt_lab.length;
			for (int i=0; i < level.m_gt_lab.length; ++i)
			{
				//Jaccard for this GT label at this time point
				double acc = 0.0;

				if (level.m_gt_match[i] > -1)
				{
					//actually, we have a match,
					//update the Jaccard accordingly
					final int intersectSize
						= level.m_match[i + m_match_lineSize*level.m_gt_match[i]];

					acc  = (double)intersectSize;
					acc /= (double)level.m_gt_size[i]
					          + (double)level.m_res_size[level.m_gt_match[i]] - acc;
				}

				//update overall stats
				seg += acc;
				++counter;

				if (doLogReports)
					log.info(String.format("GT_label=%d J=%.6g", level.m_gt_lab[i], acc));
			}

			//to be on safe side (with memory)
			gt_img = null;
			res_img = null;
		}
		fileList.close();

		seg /= (double)counter;

		log.info("---");
		log.info("SEG: "+seg);
		return (seg);
	}
}
