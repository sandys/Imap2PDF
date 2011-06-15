import java.util.Properties;
import java.util.UUID;

import java.io.Reader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.ByteArrayInputStream;

import javax.mail.Flags;
import javax.mail.Address;
import javax.mail.Folder;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.Multipart;
import javax.mail.BodyPart;
import javax.mail.UIDFolder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.search.FlagTerm;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;

import org.apache.pdfbox.exceptions.COSVisitorException;
import org.apache.pdfbox.pdmodel.edit.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDSimpleFont;
import org.apache.pdfbox.pdmodel.font.PDTrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

public class GmailInboxReader {


	public static PDDocument createPDFFromText( Reader text ) throws IOException
    {
		int fontSize = 10;
	    PDSimpleFont font = PDType1Font.HELVETICA;
        PDDocument doc = null;
        try
        {
        	
            final int margin = 40;
            float height = font.getFontDescriptor().getFontBoundingBox().getHeight()/1000;

            //calculate font height and increase by 5 percent.
            height = height*fontSize*1.05f;
            doc = new PDDocument();
            BufferedReader data = new BufferedReader( text );
            String nextLine = null;
            PDPage page = new PDPage();
            PDPageContentStream contentStream = null;
            float y = -1;
            float maxStringLength = page.getMediaBox().getWidth() - 2*margin;
            
            // There is a special case of creating a PDF document from an empty string.
            boolean textIsEmpty = true;
            
            while( (nextLine = data.readLine()) != null )
            {
            	
            	// The input text is nonEmpty. New pages will be created and added
            	// to the PDF document as they are needed, depending on the length of
            	// the text.
            	textIsEmpty = false;

                String[] lineWords = nextLine.trim().split( " " );
                int lineIndex = 0;
                while( lineIndex < lineWords.length )
                {
                    StringBuffer nextLineToDraw = new StringBuffer();
                    float lengthIfUsingNextWord = 0;
                    do
                    {
                        nextLineToDraw.append( lineWords[lineIndex] );
                        nextLineToDraw.append( " " );
                        lineIndex++;
                        if( lineIndex < lineWords.length )
                        {
                            String lineWithNextWord = nextLineToDraw.toString() + lineWords[lineIndex];
                            lengthIfUsingNextWord =
                                (font.getStringWidth( lineWithNextWord )/1000) * fontSize;
                        }
                    }
                    while( lineIndex < lineWords.length &&
                           lengthIfUsingNextWord < maxStringLength );
                    if( y < margin )
                    {
                    	// We have crossed the end-of-page boundary and need to extend the
                    	// document by another page.
                        page = new PDPage();
                        doc.addPage( page );
                        if( contentStream != null )
                        {
                            contentStream.endText();
                            contentStream.close();
                        }
                        contentStream = new PDPageContentStream(doc, page);
                        contentStream.setFont( font, fontSize );
                        contentStream.beginText();
                        y = page.getMediaBox().getHeight() - margin + height;
                        contentStream.moveTextPositionByAmount(
                            margin, y );

                    }
                    //System.out.println( "Drawing string at " + x + "," + y );

                    if( contentStream == null )
                    {
                        throw new IOException( "Error:Expected non-null content stream." );
                    }
                    contentStream.moveTextPositionByAmount( 0, -height);
                    y -= height;
                    contentStream.drawString( nextLineToDraw.toString() );
                }


            }
            
            // If the input text was the empty string, then the above while loop will have short-circuited
            // and we will not have added any PDPages to the document.
            // So in order to make the resultant PDF document readable by Adobe Reader etc, we'll add an empty page.
            if (textIsEmpty)
            {
            	doc.addPage(page);
            }
            
            if( contentStream != null )
            {
                contentStream.endText();
                contentStream.close();
            }
        }
        catch( IOException io )
        {
            if( doc != null )
            {
                doc.close();
            }
            throw io;
        }
        return doc;
    }


	public static void main(String args[]) throws IOException,COSVisitorException {
		Properties props = System.getProperties();
		props.setProperty("mail.store.protocol", "imaps");
		try {
			Session session = Session.getDefaultInstance(props, null);
			Store store = session.getStore("imaps");
			store.connect("imap.gmail.com", "sss@clearsenses.com","something");
			System.out.println(store);

			Folder inbox = store.getFolder("Inbox");
			if( ! (inbox instanceof UIDFolder)){
				System.out.println("Not UIDFolder");
				System.exit(1);
			}
			//inbox.open(Folder.READ_ONLY);
			//sss - here be dragons
			inbox.open(Folder.READ_WRITE);

			FlagTerm ft = new FlagTerm(new Flags(Flags.Flag.SEEN),false);

			//------------------------------ IMPORTANT ---------------------------
			// Searches for all unread messages
			Message messages[] = inbox.search(ft);
			// Searches for a particular id
			//Message messages[] = ((UIDFolder)inbox).getMessagesByUID(Long.parseLong("16"),Long.parseLong("16"));


			for(Message message:messages) {
				System.out.println(message);
				System.out.println(message.getSubject()+"-----"+message.getSentDate()+"----"+((UIDFolder)inbox).getUID(message));
				Address froms[] = message.getFrom();
				for(Address from:froms){
					System.out.println("Sent by: "+ from.toString());
				}

				Object o = message.getContent();
				if(o instanceof String){
					System.out.println("String!! " + (String)o);
				} else if (o instanceof Multipart){
					System.out.println("Multipart!!");
					Multipart mp = (Multipart)o;
					for( int i = 0; i < mp.getCount() ; ++i){
						BodyPart bp = mp.getBodyPart(i);
						String disposition = bp.getDisposition();
						// It's not an attachment
					    if ( disposition == null && bp instanceof MimeBodyPart ){
							MimeBodyPart mbp = (MimeBodyPart) bp;
							if ( mbp.isMimeType( "text/plain" )) {
								System.out.println("This is text");
						
								// Grab the body containing the text version
						        String txtBody = (String) mbp.getContent();
								
								//convert String into InputStream
						    	InputStream is = new ByteArrayInputStream(txtBody.getBytes());
								//read it with BufferedReader
						    	BufferedReader br
						        	= new BufferedReader(new InputStreamReader(is));
								
								PDDocument outPDF = createPDFFromText(br);
								outPDF.save( "auto-" + String.valueOf( UUID.randomUUID() ) + ".pdf" );
								break;
							}else if ( mbp.isMimeType( "text/html" )) {
								System.out.println("This is html");
								// Grab the body containing the HTML version
						        InputStream is = mbp.getInputStream();
								File f = new File("html-auto-" + String.valueOf( UUID.randomUUID() ) + ".html");
								FileOutputStream fos = new FileOutputStream(f);
								byte[] buf = new byte[4096];
								int bytesRead;
								while((bytesRead = is.read(buf))!=-1) {
									fos.write(buf, 0, bytesRead);
								}
								fos.close();
								Process p = Runtime.getRuntime().exec("./wkhtmltopdf-amd64 " + f.toString() + " " + f.toString()+".pdf" );
								break;
							}
						}
					}
				}
				// -------------------- IMPORTANT -----------------
				// change false to true 
				
				message.setFlag(Flags.Flag.SEEN,false);
				//hmm.. not required for Gmail specifically. But the standard says a bit different.
				//message.saveChanges();
			}
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
			System.exit(1);
		} catch (MessagingException e) {
			e.printStackTrace();
			System.exit(2);
		} catch (NullPointerException e){
			e.printStackTrace();
			System.exit(2);
		} catch (IOException e){
			e.printStackTrace();
			System.exit(2);
		}


}

}
