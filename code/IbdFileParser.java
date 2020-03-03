package com.lt.tool;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class IBDFileParse {

	// encoding=utf-8(字节)
	// public static int INNODB_PAGE_SIZE = 16 * 1024 * 1024;

	// Fil Header(文件头)总长度为38
	public static int FIL_PAGE_DATA = 38;

	// page offset inside space
	public static int FIL_PAGE_OFFSET = 4;

	// File page type
	public static int FIL_PAGE_TYPE = 24;

	// Types of an undo log segment */
	// public static int TRX_UNDO_INSERT = 1;

	// public static int TRX_UNDO_UPDATE = 2;

	// On a page of any file segment, data may be put starting from this offset
	// public static int FSEG_PAGE_DATA = FIL_PAGE_DATA;

	// The offset of the undo log page header on pages of the undo log
	// public static int TRX_UNDO_PAGE_HDR = FSEG_PAGE_DATA;

	// level of the node in an index tree; the leaf level is the level 0
	public static int PAGE_LEVEL = 26;

	// 页面类型
	public static Map<String, String> innodb_page_type = new HashMap<String, String>();

	// InnoDB Page 16K
	public static int INNODB_PAGE_SIZE = 1024 * 16;

	static {
		innodb_page_type.put("0000", "Freshly Allocated Page");
		innodb_page_type.put("0002", "Undo Log Page");
		innodb_page_type.put("0003", "File Segment inode");
		innodb_page_type.put("0004", "Insert Buffer Free List");
		innodb_page_type.put("0005", "Insert Buffer Bitmap");
		innodb_page_type.put("0006", "System Page");
		innodb_page_type.put("0007", "Transaction system Page");
		innodb_page_type.put("0008", "File Space Header");
		innodb_page_type.put("0009", "扩展描述页");
		innodb_page_type.put("000a", "Uncompressed BLOB Page");
		innodb_page_type.put("000b", "1st compressed BLOB Page");
		innodb_page_type.put("000c", "Subsequent compressed BLOB Page");
		innodb_page_type.put("45bf", "B-tree Node");
	}

	public static void run(String fileName, boolean writePage, boolean printInfo) {
		// 找到目标文件
		File file = new File(fileName);

		// 获取文件大小
		long size = file.length();

		long fsize = size / INNODB_PAGE_SIZE;

		InputStream is = null;

		try {
			is = new FileInputStream(file);
			// int length = is.available();

			// result map
			Map<String, Integer> results = new HashMap<String, Integer>();

			for (int i = 0; i < fsize; i++) {
				byte bt[] = new byte[INNODB_PAGE_SIZE];
				is.read(bt);

				String page_offset = mach_read_from_n(bt, FIL_PAGE_OFFSET, 4);
				String page_type = mach_read_from_n(bt, FIL_PAGE_TYPE, 2);

				// int int_offset = to10(page_offset);

				//if (int_offset < 10) {
					if ("45bf".equals(page_type)) {
						// 获取该页在索引中的层次（页节点是0），位置是38（文件头大小） + 26（页头开始的位置），层次长度为2
						String page_level = mach_read_from_n(bt, FIL_PAGE_DATA + PAGE_LEVEL, 2);
						System.out.println("page offset " + page_offset + ", page type <"
								+ innodb_page_type.get(page_type) + ">, page level <" + page_level + ">");

						if (writePage) {
							write(formatAll(bt), "page_" + page_offset + ".txt");
						}

						if (printInfo) {
							System.out.println(getFileHeader(bt));
							System.out.println(getPageHeader(bt));
							System.out.println(getPseudo(bt));
							List<String> slots = getPageDictionary(bt);
							System.out.println("Page Dictionary Info:\n" + slots);
							System.out.println("Primary Key Info:\n" + parsePrimaryKey(bt));
						}

					} else {
						// System.out.println(
						// "page offset " + page_offset + ", page type <" +
						// innodb_page_type.get(page_type) + ">");
					}
				//}

				int value = (null == results.get(innodb_page_type.get(page_type))) ? 0
						: results.get(innodb_page_type.get(page_type));

				results.put(innodb_page_type.get(page_type), ++value);
			}

			System.out.println("Total number of page:" + fsize);
			System.out.println(printResult(results));
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(is);
		}
	}

	/**
	 * 读取固定字节的内容
	 * 
	 * @param bt
	 * @return
	 */
	public static String mach_read_from_n(byte[] bt, int start, int length) {
		byte[] original = Arrays.copyOfRange(bt, start, start + length);
		return format(original);
	}

	public static String formatAll(byte[] bt) {
		int line = 0;
		StringBuilder buf = new StringBuilder();
		for (byte d : bt) {
			if (line % 16 == 0)
				buf.append(String.format("%05x: ", line));
			buf.append(String.format("%02x  ", d));
			line++;
			if (line % 16 == 0)
				buf.append("\n");
		}
		buf.append("\n");
		return buf.toString();
	}

	/**
	 * 格式化成16进制
	 * 
	 * @param bt
	 * @return
	 */
	public static String format(byte[] bt) {
		StringBuilder buf = new StringBuilder();
		for (byte d : bt) {
			buf.append(String.format("%02x", d));
		}
		return buf.toString();
	}

	public static String printResult(Map<String, Integer> results) {
		if (null == results || results.size() == 0) {
			return null;
		}

		StringBuilder sb = new StringBuilder();
		for (Entry<String, Integer> entry : results.entrySet()) {
			sb.append(entry.getKey() + ":" + entry.getValue() + "\n");
		}

		return sb.toString();
	}

	public static String help() {
		return "Get InnoDB Page Info" + "\nUsage: python py_innodb_page_info.py [OPTIONS] tablespace_file\n"
				+ "The following options may be given as the first argument:" + "\n-h\thelp "
				+ "\n-o\toutput put the result to file" + "\n-t\tnumber thread to anayle the tablespace file"
				+ "\n-v\tverbose mode";
	}

	/**
	 * close
	 * 
	 * @param c
	 */
	public static void close(Closeable c) {
		try {
			c.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * 写文件
	 * 
	 * @param s
	 */
	public static void write(String s, String filename) {
		FileWriter fileWritter = null;
		BufferedWriter bufferWritter = null;

		try {
			File file = new File(filename);

			if (!file.exists()) {
				file.createNewFile();
			}

			// true = append file
			fileWritter = new FileWriter(file.getName(), true);
			bufferWritter = new BufferedWriter(fileWritter);
			bufferWritter.write(s);

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			close(bufferWritter);
			close(fileWritter);
		}
	}

	/**
	 * file header
	 * 
	 * @param bt
	 * @return
	 */
	private static String getFileHeader(byte[] bt) {
		// 页面开头：
		// FIL_PAGE_SPACE_OR_CHECKSUM 4 space id
		String FIL_PAGE_SPACE_OR_CHECKSUM = mach_read_from_n(bt, 0, 4);
		// FIL_PAGE_OFFSET 4 页号（页号）
		String FIL_PAGE_OFFSET = mach_read_from_n(bt, 4, 4);
		// FIL_PAGE_PREV 4 上一页
		String FIL_PAGE_PREV = mach_read_from_n(bt, 8, 4);
		// FIL_PAGE_NEXT 4 下一页
		String FIL_PAGE_NEXT = mach_read_from_n(bt, 12, 4);
		// FIL_PAGE_LSN 8 日志序列号
		String FIL_PAGE_LSN = mach_read_from_n(bt, 16, 8);
		// FIL_PAGE_TYPE 2 页类型（页面类型）
		// 获取页面类型：从第24个字节开始，长度为2字节
		String FIL_PAGE_TYPE = mach_read_from_n(bt, 24, 2);
		// FIL_PAGE_FILE_FLUSH_LSN 8 文件的日志序列号，仅文件的第一页的此字段有效
		String FIL_PAGE_FILE_FLUSH_LSN = mach_read_from_n(bt, 26, 8);
		// FIL_PAGE_ARCH_LOG_NO 4 归档日志文件号
		String FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID = mach_read_from_n(bt, 34, 4);

		return "File Header Info:" + "\nFIL_PAGE_SPACE_OR_CHECKSUM           " + FIL_PAGE_SPACE_OR_CHECKSUM
				+ "\nFIL_PAGE_OFFSET                      " + FIL_PAGE_OFFSET
				+ "\nFIL_PAGE_PREV                        " + FIL_PAGE_PREV + "\nFIL_PAGE_NEXT                        "
				+ FIL_PAGE_NEXT + "\nFIL_PAGE_LSN                         " + FIL_PAGE_LSN
				+ "\nFIL_PAGE_TYPE                        " + FIL_PAGE_TYPE + "\nFIL_PAGE_FILE_FLUSH_LSN              "
				+ FIL_PAGE_FILE_FLUSH_LSN + "\nFIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID     "
				+ FIL_PAGE_ARCH_LOG_NO_OR_SPACE_ID;
	}

	/**
	 * page header
	 * 
	 * @param bt
	 * @return
	 */
	private static String getPageHeader(byte[] bt) {
		String PAGE_N_DIR_SLOTS = mach_read_from_n(bt, FIL_PAGE_DATA + 0, 2);
		String PAGE_HEAD_TOP = mach_read_from_n(bt, FIL_PAGE_DATA + 2, 2);
		String PAGE_N_HEAP = mach_read_from_n(bt, FIL_PAGE_DATA + 4, 2);
		String PAGE_FREE = mach_read_from_n(bt, FIL_PAGE_DATA + 6, 2);
		String PAGE_GARBAGE = mach_read_from_n(bt, FIL_PAGE_DATA + 8, 2);
		String PAGE_LAST_INSER = mach_read_from_n(bt, FIL_PAGE_DATA + 10, 2);
		String PAGE_DIRECTION = mach_read_from_n(bt, FIL_PAGE_DATA + 12, 2);
		String PAGE_N_DIRECTION = mach_read_from_n(bt, FIL_PAGE_DATA + 14, 2);
		String PAGE_N_RECS = mach_read_from_n(bt, FIL_PAGE_DATA + 16, 2);
		String PAGE_MAX_TRX_ID = mach_read_from_n(bt, FIL_PAGE_DATA + 18, 8);
		String PAGE_LEVEL = mach_read_from_n(bt, FIL_PAGE_DATA + 26, 2);
		String PAGE_INDEX_ID = mach_read_from_n(bt, FIL_PAGE_DATA + 28, 8);
		String PAGE_BTR_SEG_LEAF = mach_read_from_n(bt, FIL_PAGE_DATA + 36, 10);
		String PAGE_BTR_SEG_TOP = mach_read_from_n(bt, FIL_PAGE_DATA + 46, 10);

		System.out.println("Data Info:\nTotal record size is " + to10(PAGE_N_RECS) + " ,record position from 007f to "
				+ PAGE_HEAD_TOP);

		return "Page Header Info :" + "\nPAGE_N_DIR_SLOTS     " + PAGE_N_DIR_SLOTS + "\nPAGE_HEAD_TOP        "
				+ PAGE_HEAD_TOP + "\nPAGE_N_HEAP          " + PAGE_N_HEAP + "\nPAGE_FREE            " + PAGE_FREE
				+ "\nPAGE_GARBAGE         " + PAGE_GARBAGE + "\nPAGE_LAST_INSER      " + PAGE_LAST_INSER
				+ "\nPAGE_DIRECTION       " + PAGE_DIRECTION + "\nPAGE_N_DIRECTION     " + PAGE_N_DIRECTION
				+ "\nPAGE_N_RECS          " + PAGE_N_RECS + "\nPAGE_MAX_TRX_ID      " + PAGE_MAX_TRX_ID
				+ "\nPAGE_LEVEL           " + PAGE_LEVEL + "\nPAGE_INDEX_ID        " + PAGE_INDEX_ID
				+ "\nPAGE_BTR_SEG_LEAF    " + PAGE_BTR_SEG_LEAF + "\nPAGE_BTR_SEG_TOP     " + PAGE_BTR_SEG_TOP;
	}

	private static String getPseudo(byte[] bt) {
		String I_RH = mach_read_from_n(bt, 94, 5);
		// 记录类型为char（8）
		String I_RC = mach_read_from_n(bt, 99, 8);
		String S_RH = mach_read_from_n(bt, 107, 5);
		// 记录类型为char（8）
		String S_RC = mach_read_from_n(bt, 112, 8);
		return "Infimum Record Info:" + "\nrecord header : " + I_RH + "\nValue         : " + I_RC
				+ "\nSupremum Record Info:" + "\nrecord header : " + S_RH + "\nValue         : " + S_RC;
	}

	public static String parseData(byte[] bt) {
		return null;
	}

	/**
	 * 仅支持类型是int型的转换
	 * 
	 * @param bt
	 * @return
	 */
	public static List<String> parsePrimaryKey(byte[] bt) {

		List<String> datas = new ArrayList<String>();

		String PAGE_HEAD_TOP = mach_read_from_n(bt, FIL_PAGE_DATA + 2, 2);
		// int 型长度为4
		final int size = 4;
		String offset = "";
		int position = 0;
		String slot = "0063";

		position = to10(slot);
		int end = to10(PAGE_HEAD_TOP);
		int i = 0;
		// 为防止死循环，最多打印1000个
		while (i++ <= 1000) {

			offset = mach_read_from_n(bt, position - 2, 2);
			// System.out.println(offset);
			if ("0000".equals(offset)) {
				break;
			}

			position = to10(offset) + position;

			if (position > end) {
				break;
			}

			// if data start with 8,无符号型
			datas.add(format(Arrays.copyOfRange(bt, position, position + size)));
		}

		return datas;
	}

	/**
	 * get page dictionary
	 * 
	 * @param bt
	 * @return
	 */
	private static List<String> getPageDictionary(byte[] bt) {
		List<String> slots = new ArrayList<String>();

		for (int i = bt.length - 10;; i = i - 2) {
			byte[] temp = Arrays.copyOfRange(bt, i, i + 2);

			slots.add(format(temp));

			if ("0070".equals(format(temp))) {
				break;
			}
		}

		return slots;
	}

	public static int to10(String hex) {
		return Integer.parseInt(hex, 16);
	}

	/**
	 * 字符串转换成为16进制(无需Unicode编码)
	 * 
	 * @param str
	 * @return
	 */
	public static String str2HexStr(String str) {
		char[] chars = "0123456789ABCDEF".toCharArray();
		StringBuilder sb = new StringBuilder("");
		byte[] bs = str.getBytes();
		int bit;
		for (int i = 0; i < bs.length; i++) {
			bit = (bs[i] & 0x0f0) >> 4;
			sb.append(chars[bit]);
			bit = bs[i] & 0x0f;
			sb.append(chars[bit]);
			// sb.append(' ');
		}
		return sb.toString().trim();
	}

	/**
	 * 16进制字符串转换为字符串
	 * 
	 * @param s
	 * @return
	 */
	public static String hexStringToString(String s) {
		if (s == null || s.equals("")) {
			return null;
		}
		s = s.replace(" ", "");
		byte[] baKeyword = new byte[s.length() / 2];
		for (int i = 0; i < baKeyword.length; i++) {
			try {
				baKeyword[i] = (byte) (0xff & Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16));
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		try {
			s = new String(baKeyword, "gbk");
			new String();
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		return s;
	}

	public static void main(String[] args) {
		run(args[0], false, false);
		// run("sbtest1.ibd");
		// System.out.println(hexStringToString("32 35 34 38 35 37 37 20"));
	}
}
