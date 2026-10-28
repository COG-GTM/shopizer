package com.salesmanager.core.business.utils;

import java.nio.file.Path;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.business.exception.ServiceException;

/**
 * Validation of client supplied file names and confinement of file system
 * paths built from them.
 */
public final class FilePathUtils {

	private static final int MAX_FILE_NAME_LENGTH = 255;

	private FilePathUtils() {
	}

	/**
	 * A safe file name is a single path element: not blank, at most 255
	 * characters, no path separator, drive or stream delimiter, no control
	 * character and not made only of dots and spaces (".", "..", ". .").
	 */
	public static boolean isSafeFileName(String fileName) {
		if (StringUtils.isBlank(fileName) || fileName.length() > MAX_FILE_NAME_LENGTH) {
			return false;
		}
		for (int i = 0; i < fileName.length(); i++) {
			char c = fileName.charAt(i);
			if (c == '/' || c == '\\' || c == ':' || c < 0x20 || c == 0x7f) {
				return false;
			}
		}
		return !StringUtils.containsOnly(fileName, '.', ' ');
	}

	public static String requireSafeFileName(String fileName) throws ServiceException {
		if (!isSafeFileName(fileName)) {
			throw new ServiceException("Invalid file name [" + StringUtils.abbreviate(
					StringUtils.defaultString(fileName).replaceAll("\\p{Cntrl}", "?"), 64) + "]");
		}
		return fileName;
	}

	/**
	 * Resolves a relative, '/' separated path (e.g. a store code or a CMS
	 * folder such as "/DEFAULT/12") under base. Leading and repeated slashes
	 * are ignored; every element must be a safe file name.
	 */
	public static Path resolveRelative(Path base, String relativePath) throws ServiceException {
		Path resolved = base;
		if (relativePath != null) {
			for (String element : relativePath.split("/")) {
				if (element.isEmpty()) {
					continue;
				}
				resolved = resolved.resolve(requireSafeFileName(element));
			}
		}
		return requireWithin(base, resolved);
	}

	/**
	 * Resolves a single file name under base.
	 */
	public static Path resolveFile(Path base, String fileName) throws ServiceException {
		return requireWithin(base, base.resolve(requireSafeFileName(fileName)));
	}

	public static Path requireWithin(Path base, Path candidate) throws ServiceException {
		Path normalizedBase = base.toAbsolutePath().normalize();
		Path normalized = candidate.toAbsolutePath().normalize();
		if (!normalized.startsWith(normalizedBase)) {
			throw new ServiceException("Path [" + candidate + "] is outside of [" + base + "]");
		}
		return normalized;
	}

}
