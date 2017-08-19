/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package de.mpicbg.ulman;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.log.LogService;
import net.imagej.ImageJ;

import de.mpicbg.ulman.workers.TRA;

@Plugin(type = Command.class, menuPath = "Plugins>CTC>TRA")
public class plugin_TRA implements Command
{

	@Parameter
	private LogService log;

	@Parameter(label = "Path to ground-truth folder: ",
		description = "Path should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt")
	private String gtPath;

	@Parameter(label = "Path to computed result folder: ",
		description = "Path should contain result files directly: mask???.tif and res_track.txt")
	private String resPath;

	//the GUI path entry function:
	@Override
	public void run()
	{
		try {
			//start up the worker class
			final TRA tra
				= new TRA(log);

			//do the calculation
			final float traValue
				= tra.calculate(gtPath,resPath);

			//report the result
			log.info("TRA is: " + traValue);
		}
		catch (RuntimeException e) {
			log.error("TRA problem: "+e.getMessage());
		}
		catch (Exception e) {
			log.error("TRA error: "+e.getMessage());
		}
	}


	//the CLI path entry function:
	public static void main(final String... args)
	{
		//check the input parameters
		if (args.length != 2)
		{
			System.out.println("Incorrect number of parameters, expecting exactly two parameters.");
			System.out.println("Parameters: GTpath RESpath\n");
			System.out.println("GTpath should contain folder TRA and files: TRA/man_track???.tif and TRA/man_track.txt");
			System.out.println("RESpath should contain result files directly: mask???.tif and res_track.txt");
			return;
		}

		//head less variant:
		//start up our own ImageJ without GUI
		final ImageJ ij = new net.imagej.ImageJ();

		try {
			//start up the worker class
			final TRA tra
				= new TRA(ij.log());

			//do the calculation
			final float traValue
				= tra.calculate(args[0],args[1]);

			//report the result
			ij.log().info("TRA is: " + traValue);
		}
		catch (RuntimeException e) {
			ij.log().error("TRA problem: "+e.getMessage());
		}
		catch (Exception e) {
			ij.log().error("TRA error: "+e.getMessage());
		}

		//and close the IJ instance...
		ij.appEvent().quit();
	}
}