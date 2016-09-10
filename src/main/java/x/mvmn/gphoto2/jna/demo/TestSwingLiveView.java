package x.mvmn.gphoto2.jna.demo;

import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;

import x.mvmn.gphoto2.jna.Camera;
import x.mvmn.gphoto2.jna.Camera.ByReference;
import x.mvmn.gphoto2.jna.Gphoto2Library;

/**
 * @author Mykola Makhin
 *
 */
public class TestSwingLiveView {

	public static void main(String[] args) throws Exception {
		final Camera camera = newCamera();

		final PointerByReference context = newContext();
		initCamera(camera, context);
		// check(Gphoto2Library.INSTANCE.gp_camera_trigger_capture(camera, context));

		final JFrame frame = new JFrame();
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		frame.getContentPane().setLayout(new FlowLayout());
		frame.setPreferredSize(Toolkit.getDefaultToolkit().getScreenSize());
		frame.pack();
		frame.setVisible(true);
		final AtomicBoolean finish = new AtomicBoolean(false);
		Thread previewThread = new Thread() {
			public void run() {
				while (!finish.get()) {
					final PointerByReference pbrFile = capturePreview(camera, context);
					try {
						final BufferedImage image = ImageIO.read(new ByteArrayInputStream(getCameraFileData(pbrFile, camera, context)));

						SwingUtilities.invokeLater(new Runnable() {
							public void run() {
								frame.getContentPane().removeAll();
								frame.getContentPane().add(new JLabel(new ImageIcon(image)));
								frame.getContentPane().invalidate();
								frame.getContentPane().revalidate();
								frame.getContentPane().repaint();
							}
						});
						Thread.yield();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
						freeCameraFile(pbrFile);
					}
				}

				exitCam(camera, context);
			}
		};
		frame.addWindowListener(new WindowListener() {
			public void windowClosing(WindowEvent e) {
				finish.set(true);
				frame.dispose();
			}

			public void windowOpened(WindowEvent e) {
			}

			public void windowIconified(WindowEvent e) {
			}

			public void windowDeiconified(WindowEvent e) {
			}

			public void windowDeactivated(WindowEvent e) {
			}

			public void windowClosed(WindowEvent e) {
			}

			public void windowActivated(WindowEvent e) {
			}
		});
		previewThread.start();
	}

	public static int check(int retVal) {
		if (retVal < 0) {
			System.err.println("Error " + retVal);
			new Exception().printStackTrace();
		}

		return retVal;
	}

	public static int exitCam(Camera camera, PointerByReference context) {
		return check(Gphoto2Library.INSTANCE.gp_camera_exit(camera, context));
	}

	public static int freeCameraFile(PointerByReference pbrFile) {
		return check(Gphoto2Library.INSTANCE.gp_file_unref(pbrFile));
	}

	public static int initCamera(Camera camera, PointerByReference context) {
		return check(Gphoto2Library.INSTANCE.gp_camera_init(camera, context));
	}

	public static PointerByReference newContext() {
		return Gphoto2Library.INSTANCE.gp_context_new();
	}

	public static ByReference newCamera() {
		Camera.ByReference[] p2CamByRef = new Camera.ByReference[] { new Camera.ByReference() };
		check(Gphoto2Library.INSTANCE.gp_camera_new(p2CamByRef));
		return p2CamByRef[0];
	}

	private static final Object LOCK_OBJECT_CAPTURE = new Object();

	public static PointerByReference capturePreview(Camera camera, PointerByReference context) {
		PointerByReference pbrFile = new PointerByReference();
		{
			check(Gphoto2Library.INSTANCE.gp_file_new(pbrFile));
			// PointerByReference pFile = new PointerByReference();
			// pFile.setPointer(pbrFile.getValue());
			// pbrFile = pFile;
			pbrFile.setPointer(pbrFile.getValue());
		}
		synchronized (LOCK_OBJECT_CAPTURE) {
			check(Gphoto2Library.INSTANCE.gp_camera_capture_preview(camera, pbrFile, context));
		}
		return pbrFile;
	}

	public static byte[] getCameraFileData(PointerByReference cameraFile, Camera camera, PointerByReference context) {
		PointerByReference pref = new PointerByReference();
		LongByReference longByRef = new LongByReference();
		int captureRes = check(Gphoto2Library.INSTANCE.gp_file_get_data_and_size(cameraFile, pref, longByRef));
		if (captureRes >= 0) {
			return pref.getValue().getByteArray(0, (int) longByRef.getValue());
		} else {
			return null;
		}
	}
}
