package com.google.zxing.oned;

import java.util.Arrays;
import java.util.Map;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ChecksumException;
import com.google.zxing.DecodeHintType;
import com.google.zxing.FormatException;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.common.BitArray;

public class AhBarcodeDecoder extends OneDReader {

  @Override
  public Result decodeRow(int rowNumber, BitArray row, Map<DecodeHintType, ?> hints)
      throws NotFoundException, ChecksumException, FormatException {

    int[] allCounters = new int[27];
    recordPattern(row, 0, allCounters);
    int[] counters = Arrays.stream(allCounters, 1, 26).toArray();
    int barcodeWidth = Arrays.stream(counters).sum();
    float narrowLineWidth = (float) (barcodeWidth / 38.0);

    int width = row.getSize();

    int[] normalizedWidth = Arrays.stream(counters).map(e -> Math.round(e / narrowLineWidth)).toArray();

    int[] target = Arrays.stream(normalizedWidth).map(e -> Math.round(narrowLineWidth * (e))).toArray();
    float variance = patternMatchVariance(counters, target, (float) 0.5 * narrowLineWidth);

    int[] bbits = new int[13];

    for (int i = 0; i < 12; i++) {
      if (normalizedWidth[i * 2] + normalizedWidth[i * 2 + 1] == 3) {
        bbits[i] = normalizedWidth[i * 2] == 1 ? 0 : 1;
      } else {
        throw NotFoundException.getNotFoundInstance();
      }
    }

    bbits[12] = normalizedWidth[24] - 1;

    int checksum = Arrays.stream(bbits, 0, 10).sum() % 2;

    if (bbits[0] != 0 || bbits[12] != 1) {
      throw NotFoundException.getNotFoundInstance();
    }

    if (checksum == 0) {
      if (bbits[10] != 0 || bbits[11] != 1) {
        throw ChecksumException.getChecksumInstance();
      }
    } else if (checksum == 1) {
      if (bbits[10] != 1 || bbits[11] != 0) {
        throw ChecksumException.getChecksumInstance();
      }
    } else {
      throw ChecksumException.getChecksumInstance();
    }

    StringBuilder chars = new StringBuilder();
    Arrays.stream(bbits).forEachOrdered(val -> chars.append(val == 0 ? "0" : "1"));
    String byteString = chars.toString();

    byte b1 = Byte.parseByte(byteString.substring(0, 8), 2);
    byte b2 = Byte.parseByte(byteString.substring(8), 2);
    byte[] b = { b1, b2 };
    Integer barcodeValue = Integer.parseInt(byteString.substring(2, 10), 2);

    Result resultObject = new Result(barcodeValue.toString(), b, new ResultPoint[] {
        new ResultPoint(allCounters[0], rowNumber), new ResultPoint(width - allCounters[26], rowNumber) },
        BarcodeFormat.AH);
    return resultObject;
  }

}
