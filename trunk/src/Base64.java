/*
Base64 ARCII armouring.

copyright (c) 1999-2006 Roedy Green, Canadian Mind Products
may be copied and used freely for any purpose but military.

  Freeware from:
  Roedy Green
  Canadian Mind Products
  #327 - 964 Heywood Avenue
  Victoria, BC Canada
  V8V 2Y5
  tel:(250) 361-9093
  mailto:roedyg@mindprod.com

  Encode arbitrary binary into printable ASCII using BASE64 encoding. very
  loosely based on the Base64 Reader by:
  Dr. Mark Thornton<br>
  Optrak Distribution Software Ltd.
  http://www.optrak.co.uk
  and Kevin Kelley's http://www.ruralnet.net/~kelley/java/Base64.java<br>
 
  version history
  version 1.4 2002 February 15 -- correct bugs with uneven line lengths,
  allow you to configure line separator. now need Base64 object and instance
  methods. new mailing address.

  version 1.3 2000 September 12 -- fix problems with estimating output
  length in encode

  version 1.2 2000 September 09 -- now handles decode as well.

  version 1.1 1999 December 04 -- more symmetrical encoding algorithm.
  more accurate StringBuffer allocation size.

  version 1.0 1999 December 03 -- posted in comp.lang.java.programmer.

  TODO Streams or files.
 */

package com.mindprod.base64;

/**
 * Encode arbitrary binary into printable ASCII using BASE64 encoding.
 * <p/>
 * Base64 is a way of encoding 8-bit characters using only ASCII printable
 * characters similar to UUENCODE. UUENCODE includes a filename where BASE64
 * does not. The spec is described in RFC 2045. Base64 is a scheme where 3 bytes
 * are concatenated, then split to form 4 groups of 6-bits each; and each 6-bits
 * gets translated to an encoded printable ASCII character, via a table lookup.
 * An encoded string is therefore longer than the original by about 1/3. The "="
 * character is used to pad the end. Base64 is used, among other things, to
 * encode the user:password string in an Authorization: header for HTTP. Don't
 * confuse Base64 with x-www-form-urlencoded which is handled by
 * Java.net.URLEncoder.encode/decode
 * <p/>
 * If you don't like this code, there is another implementation at
 * http://www.ruffboy.com/download.htm Sun has an undocumented method called
 * sun.misc.Base64Encoder.encode. You could use hex, simpler to code, but not as
 * compact.
 * <p/>
 * If you wanted to encode a giant file, you could do it in large chunks that
 * are even multiples of 3 bytes, except for the last chunk, and append the
 * outputs.
 * <p/>
 * To encode a string, rather than binary data java.net.URLEncoder may be
 * better. See printable characters in the Java glossary for a discussion of the
 * differences.
 * <p/>
 * Base 64 armouring uses only the characters A-Z a-z 0-9 +/=. This makes it
 * suitable for encoding binary data as SQL strings, that will work no matter
 * what the encoding. Unfortunately + / and = all have special meaning in URLs.
 */
public class Base64 {

    // ------------------------------ FIELDS ------------------------------

    /**
     * used to disable test driver
     */
    public static final boolean DEBUGGING = false;

    /**
     * Marker value for chars we just ignore, e.g. \n \r high ascii
     */
    static final int IGNORE = -1;

    /**
     * Marker for = trailing pad
     */
    static final int PAD = -2;

    /**
     * binary value encoded by a given letter of the alphabet 0..63
     */
    private static int[] cv;

    /**
     * letter of the alphabet used to encode binary values 0..63
     */
    private static char[] vc;

    /**
     * binary value encoded by a given letter of the alphabet 0..63, overridden
     * in Base64u
     */
    int[] charToValue;

    /**
     * special character 1, will be - in Base64u
     */
    char spec1 = '+';

    /**
     * special character 2, will be _ in Base64u
     */
    char spec2 = '/';

    /**
     * special character 3, will be * in Base64u
     */
    char spec3 = '=';

    /**
     * letter of the alphabet used to encode binary values 0..63, overridden in
     * Base64u.
     */
    char[] valueToChar;

    /**
     * how we separate lines, e.g. \n, \r\n, \r etc.
     */
    private String lineSeparator = System.getProperty( "line.separator" );

    /**
     * max chars per line, excluding lineSeparator. A multiple of 4.
     */
    private int lineLength = 72;

    // -------------------------- STATIC METHODS --------------------------

    /**
     * debug display array as if there were 8-bit Latin-1
     *
     * @param b byte array to display
     */
    public static void display( byte[] b )
        {
        for ( int i = 0; i < b.length; i++ )
            {
            System.out.print( (char) b[ i ] );
            }
        System.out.println();
        }

    /**
     * debug display array as hex
     *
     * @param b byte array to display
     */
    public static void show( byte[] b )
        {
        for ( int i = 0; i < b.length; i++ )
            {
            System.out.print( Integer.toHexString( b[ i ] & 0xff ) + " " );
            }
        System.out.println();
        }

    // --------------------------- CONSTRUCTORS ---------------------------

    /**
     * constructor
     */
    public Base64()
        {
        spec1 = '+';
        spec2 = '/';
        spec3 = '=';
        initTables();
        }

    // --------------------- GETTER / SETTER METHODS ---------------------

    /**
     * How lines are separated. Ignored by decode.
     *
     * @param lineSeparator may be "" but not null. Usually contains only a combination of
     *                      chars \n and \r. Could be any chars not in set A-Z a-z 0-9 + /.
     */
    public void setLineSeparator( String lineSeparator )
        {
        this.lineSeparator = lineSeparator;
        }

    // -------------------------- OTHER METHODS --------------------------

    /**
     * decode a well-formed complete Base64 string back into an array of bytes.
     * It must have an even multiple of 4 data characters (not counting \n),
     * padded out with = as needed.
     *
     * @param s base64-encoded string
     *
     * @return plaintext as a byte array.
     */
    public byte[] decode( String s )
        {
        // estimate worst case size of output array, no embedded newlines.
        byte[] b = new byte[ ( s.length() / 4 ) * 3 ];

        // tracks where we are in a cycle of 4 input chars.
        int cycle = 0;

        // where we combine 4 groups of 6 bits and take apart as 3 groups of 8.
        int combined = 0;

        // how many bytes we have prepared.
        int j = 0;
        // will be an even multiple of 4 chars, plus some embedded \n
        int len = s.length();
        int dummies = 0;
        for ( int i = 0; i < len; i++ )
            {
            int c = s.charAt( i );
            int value = ( c <= 255 ) ? charToValue[ c ] : IGNORE;
            // there are two magic values PAD (=) and IGNORE.
            switch ( value )
                {
                case IGNORE :
                    // e.g. \n, just ignore it.
                    break;

                case PAD :
                    value = 0;
                    dummies++;
                    // deliberate fallthrough
                default :
                    /* regular value character */
                    switch ( cycle )
                        {
                        case 0 :
                            combined = value;
                            cycle = 1;
                            break;

                        case 1 :
                            combined <<= 6;
                            combined |= value;
                            cycle = 2;
                            break;

                        case 2 :
                            combined <<= 6;
                            combined |= value;
                            cycle = 3;
                            break;

                        case 3 :
                            combined <<= 6;
                            combined |= value;
                            // we have just completed a cycle of 4 chars.
                            // the four 6-bit values are in combined in
                            // big-endian order
                            // peel them off 8 bits at a time working lsb to msb
                            // to get our original 3 8-bit bytes back

                            b[ j + 2 ] = (byte) combined;
                            combined >>>= 8;
                            b[ j + 1 ] = (byte) combined;
                            combined >>>= 8;
                            b[ j ] = (byte) combined;
                            j += 3;
                            cycle = 0;
                            break;
                        }
                    break;
                }
            } // end for
        if ( cycle != 0 )
            {
            throw new ArrayIndexOutOfBoundsException(
                    "Input to decode not an even multiple of 4 characters; pad with "
                    + spec3 );
            }
        j -= dummies;
        if ( b.length != j )
            {
            byte[] b2 = new byte[ j ];
            System.arraycopy( b, 0, b2, 0, j );
            b = b2;
            }
        return b;
        }// end decode

    /**
     * Encode an arbitrary array of bytes as Base64 printable ASCII. It will be
     * broken into lines of 72 chars each. The last line is not terminated with
     * a line separator. The output will always have an even multiple of data
     * characters, exclusive of \n. It is padded out with =.
     *
     * @param b byte array to encode, typically produced by a
     *          ByteArrayOutputStream
     *
     * @return base-64 encoded String, not char[] or byte[].
     */
    public String encode( byte[] b )
        {
        // Each group or partial group of 3 bytes becomes four chars
        // covered quotient
        int outputLength = ( ( b.length + 2 ) / 3 ) * 4;

        // account for trailing newlines, on all but the very last line
        if ( lineLength != 0 )
            {
            int lines = ( outputLength + lineLength - 1 ) / lineLength - 1;
            if ( lines > 0 )
                {
                outputLength += lines * lineSeparator.length();
                }
            }

        // must be local for recursion to work.
        StringBuffer sb = new StringBuffer( outputLength );

        // must be local for recursion to work.
        int linePos = 0;

        // first deal with even multiples of 3 bytes.
        int len = ( b.length / 3 ) * 3;
        int leftover = b.length - len;
        for ( int i = 0; i < len; i += 3 )
            {
            // Start a new line if next 4 chars won't fit on the current line
            // We can't encapsulete the following code since the variable need
            // to
            // be local to this incarnation of encode.
            linePos += 4;
            if ( linePos > lineLength )
                {
                if ( lineLength != 0 )
                    {
                    sb.append( lineSeparator );
                    }
                linePos = 4;
                }

            // get next three bytes in unsigned form lined up,
            // in big-endian order
            int combined = b[ i + 0 ] & 0xff;
            combined <<= 8;
            combined |= b[ i + 1 ] & 0xff;
            combined <<= 8;
            combined |= b[ i + 2 ] & 0xff;

            // break those 24 bits into a 4 groups of 6 bits,
            // working LSB to MSB.
            int c3 = combined & 0x3f;
            combined >>>= 6;
            int c2 = combined & 0x3f;
            combined >>>= 6;
            int c1 = combined & 0x3f;
            combined >>>= 6;
            int c0 = combined & 0x3f;

            // Translate into the equivalent alpha character
            // emitting them in big-endian order.
            sb.append( valueToChar[ c0 ] );
            sb.append( valueToChar[ c1 ] );
            sb.append( valueToChar[ c2 ] );
            sb.append( valueToChar[ c3 ] );
            }

        // deal with leftover bytes
        switch ( leftover )
            {
            case 0 :
            default :
                // nothing to do
                break;

            case 1 :
                // One leftover byte generates xx==
                // Start a new line if next 4 chars won't fit on the current
                // line
                linePos += 4;
                if ( linePos > lineLength )
                    {
                    if ( lineLength != 0 )
                        {
                        sb.append( lineSeparator );
                        }
                    linePos = 4;
                    }

                // Handle this recursively with a faked complete triple.
                // Throw away last two chars and replace with ==
                sb.append( encode( new byte[] {b[ len ], 0, 0} ).substring( 0,
                                                                            2 ) );
                sb.append( spec3 );
                sb.append( spec3 );
                break;

            case 2 :
                // Two leftover bytes generates xxx=
                // Start a new line if next 4 chars won't fit on the current
                // line
                linePos += 4;
                if ( linePos > lineLength )
                    {
                    if ( lineLength != 0 )
                        {
                        sb.append( lineSeparator );
                        }
                    linePos = 4;
                    }
                // Handle this recursively with a faked complete triple.
                // Throw away last char and replace with =
                sb.append( encode( new byte[] {b[ len ],
                                               b[ len + 1 ],
                                               0} ).substring( 0, 3 ) );
                sb.append( spec3 );
                break;
            } // end switch;

        if ( outputLength != sb.length() )
            {
            System.out
                    .println(
                            "oops: minor program flaw: output length mis-estimated" );
            System.out.println( "estimate:" + outputLength );
            System.out.println( "actual:" + sb.length() );
            }
        return sb.toString();
        }// end encode

    /**
     * Initialise both static and instance table.
     */
    private void initTables()
        {
        /* initialise valueToChar and charToValue tables */

        if ( vc == null )
            {
            // statics are not initialised yet

            vc = new char[ 64 ];

            cv = new int[ 256 ];

            // build translate valueToChar table only once.
            // 0..25 -> 'A'..'Z'
            for ( int i = 0; i <= 25; i++ )
                {
                vc[ i ] = (char) ( 'A' + i );
                }
            // 26..51 -> 'a'..'z'
            for ( int i = 0; i <= 25; i++ )
                {
                vc[ i + 26 ] = (char) ( 'a' + i );
                }
            // 52..61 -> '0'..'9'
            for ( int i = 0; i <= 9; i++ )
                {
                vc[ i + 52 ] = (char) ( '0' + i );
                }
            vc[ 62 ] = spec1;
            vc[ 63 ] = spec2;

            // build translate charToValue table only once.
            for ( int i = 0; i < 256; i++ )
                {
                cv[ i ] = IGNORE; // default is to ignore
                }

            for ( int i = 0; i < 64; i++ )
                {
                cv[ vc[ i ] ] = i;
                }

            cv[ spec3 ] = PAD;
            }
        valueToChar = vc;
        charToValue = cv;
        }

    /**
     * determines how long the lines are that are generated by encode. Ignored
     * by decode.
     *
     * @param length 0 means no newlines inserted. Must be a multiple of 4.
     */
    public void setLineLength( int length )
        {
        this.lineLength = ( length / 4 ) * 4;
        }

    // --------------------------- main() method ---------------------------

    /**
     * test driver
     *
     * @param args not used
     */
    public static void main( String[] args )
        {
        if ( DEBUGGING )
            {
            byte[] a = {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0};
            byte[] b = {(byte) 0x03, (byte) 0xf0, (byte) 0x3f};
            byte[] c = {(byte) 0x00, (byte) 0x00, (byte) 0x00};
            byte[] d = {(byte) 0xff, (byte) 0xff, (byte) 0xff};
            byte[] e = {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0, (byte) 1};
            byte[] f =
                    {(byte) 0xfc, (byte) 0x0f, (byte) 0xc0, (byte) 1, (byte) 2};
            byte[] g =
                    {(byte) 0xfc,
                     (byte) 0x0f,
                     (byte) 0xc0,
                     (byte) 1,
                     (byte) 2,
                     (byte) 3};
            byte[] h = "AAAAAAAAAAB".getBytes();

            show( a );
            show( b );
            show( c );
            show( d );
            show( e );
            show( f );
            show( g );
            show( h );
            Base64 b64 = new Base64();
            show( b64.decode( b64.encode( a ) ) );
            show( b64.decode( b64.encode( b ) ) );
            show( b64.decode( b64.encode( c ) ) );
            show( b64.decode( b64.encode( d ) ) );
            show( b64.decode( b64.encode( e ) ) );
            show( b64.decode( b64.encode( f ) ) );
            show( b64.decode( b64.encode( g ) ) );
            show( b64.decode( b64.encode( h ) ) );
            b64.setLineLength( 8 );
            show( ( b64.encode( h ) ).getBytes() );
            }
        }// end main
} // end Base64
