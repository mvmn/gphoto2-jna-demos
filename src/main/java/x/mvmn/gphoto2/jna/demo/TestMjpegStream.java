package x.mvmn.gphoto2.jna.demo;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.sun.jna.ptr.PointerByReference;

import x.mvmn.gphoto2.jna.Camera;

public class TestMjpegStream {

	private static final byte[] prefix = ("--BoundaryString\r\n" + "Content-type: image/jpeg\r\n" + "Content-Length: ").getBytes();
	private static final byte[] separator = "\r\n\r\n".getBytes();

	private static final ConcurrentLinkedQueue<byte[]> captures = new ConcurrentLinkedQueue<byte[]>();

	public static void main(String[] args) throws Exception {
		Server server = new Server(8123);
		server.setStopAtShutdown(true);

		final Camera camera = TestSwingLiveView.newCamera();
		final PointerByReference context = TestSwingLiveView.newContext();
		TestSwingLiveView.initCamera(camera, context);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				TestSwingLiveView.exitCam(camera, context);
			}
		});

		final ServletContextHandler servletContextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
		servletContextHandler.setContextPath("/");
		servletContextHandler.addServlet(new ServletHolder(new HttpServlet() {
			private static final long serialVersionUID = -6610127379314108183L;

			@Override
			public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
				response.setContentType("multipart/x-mixed-replace; boundary=--BoundaryString");
				final OutputStream outputStream = response.getOutputStream();

				Executors.newWorkStealingPool();
				final int cpuCoresCount = Runtime.getRuntime().availableProcessors();
				System.out.println("Live view capture threads: " + cpuCoresCount);
				final Thread[] threads = new Thread[cpuCoresCount];
				for (int i = 0; i < cpuCoresCount; i++) {
					final Thread thread = new Thread() {
						public void run() {
							while (true) {
								PointerByReference cameraFile = null;
								try {
									cameraFile = TestSwingLiveView.capturePreview(camera, context);
									byte[] jpeg = TestSwingLiveView.getCameraFileData(cameraFile, camera, context);
									TestSwingLiveView.freeCameraFile(cameraFile);
									captures.add(jpeg);
								} catch (Exception e) {
									e.printStackTrace();
									break;
								} finally {
									if (cameraFile != null) {
										try {
											TestSwingLiveView.freeCameraFile(cameraFile);
										} catch (Throwable t) {
											t.printStackTrace();
										}
									}
								}
							}
						}
					};
					threads[i] = thread;
					thread.start();
				}
				while (true) {
					try {
						byte[] jpeg = captures.poll();
						if (jpeg != null) {
							// write the image and wrapper
							outputStream.write(prefix);
							outputStream.write(String.valueOf(jpeg.length).getBytes());
							outputStream.write(separator);
							outputStream.write(jpeg);
							outputStream.write(separator);
							outputStream.flush();
							System.gc();
						}
						Thread.yield();
					} catch (Exception e) {
						e.printStackTrace();
						break;
					} finally {
						for (Thread thread : threads) {
							thread.interrupt();
						}
					}
				}
			}
		}), "/stream.mjpeg");

		server.setHandler(servletContextHandler);
		server.start();
		server.join();
	}

}
